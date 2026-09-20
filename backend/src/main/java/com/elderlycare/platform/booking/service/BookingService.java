package com.elderlycare.platform.booking.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.elderlycare.platform.booking.api.*;
import com.elderlycare.platform.booking.domain.*;
import com.elderlycare.platform.booking.domain.BookingTypes.*;
import com.elderlycare.platform.booking.mapper.*;
import com.elderlycare.platform.catalog.service.ServiceCatalogService;
import com.elderlycare.platform.common.api.*;
import com.elderlycare.platform.elder.mapper.ElderProfileMapper;
import com.elderlycare.platform.elder.service.ElderCrypto;
import com.elderlycare.platform.identity.domain.UserAccount;
import com.elderlycare.platform.identity.mapper.UserAccountMapper;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import java.time.*;
import java.util.*;
import static com.elderlycare.platform.booking.service.BookingAccess.*;

@Service
public class BookingService {
    private final BookingAccess access;
    private final BookingMapper bookings;
    private final BookingQueries queries;
    private final EventMapper events;
    private final WorkerMapper workers;
    private final ElderProfileMapper elders;
    private final UserAccountMapper accounts;
    private final ServiceCatalogService catalog;
    private final BookingCrypto crypto;
    private final ElderCrypto elderCrypto;
    private final BookingRules rules;

    /** 预约与履约使用同一状态源，装配权限、目录、人员、加密和记录组件。 */
    public BookingService(BookingAccess access, BookingMapper bookings, BookingQueries queries, EventMapper events,
            WorkerMapper workers, ElderProfileMapper elders, UserAccountMapper accounts, ServiceCatalogService catalog,
            BookingCrypto crypto, ElderCrypto elderCrypto, BookingRules rules) {
        this.access = access; this.bookings = bookings; this.queries = queries; this.events = events;
        this.workers = workers; this.elders = elders; this.accounts = accounts; this.catalog = catalog;
        this.crypto = crypto; this.elderCrypto = elderCrypto; this.rules = rules;
    }
    /** 根据真实角色返回本社区预约、本人/获授权预约或本人工作任务。 */
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public PageResponse<BookingResponses.Summary> list(Authentication auth, PageQuery page, Status status) {
        var actor = access.actor(auth); var now = Instant.now(); var state = status == null ? null : status.name();
        long total = queries.count(actor, state, now);
        return new PageResponse<>(queries.list(actor, state, page.offset(), page.pageSize(), now).stream()
                .map(this::summary).toList(), page.page(), page.pageSize(), total);
    }
    /** 详情仅按业务关系解密必要资料，浏览行为留存元数据。 */
    @Transactional
    public BookingResponses.Detail detail(Authentication auth, long id) {
        var actor = access.actor(auth); var row = visible(actor, id);
        access.audit(actor, id, "BOOKING_VIEW"); return detail(actor, row);
    }
    /** 分页返回完整处理进度；每次请求重新检查绑定、授权或人员任务关系。 */
    @Transactional
    public PageResponse<BookingResponses.Event> history(Authentication auth, long id, PageQuery page) {
        var actor = access.actor(auth); visible(actor, id);
        var q = new QueryWrapper<EventRow>().eq("community_id", actor.getCommunityId()).eq("booking_id", id);
        long total = events.selectCount(q);
        var items = events.selectList(q.orderByDesc("version","id").last("LIMIT " + page.pageSize() + " OFFSET " + page.offset()))
                .stream().map(e -> new BookingResponses.Event(e.id.toString(), e.actorId.toString(), e.action,
                        Status.valueOf(e.fromStatus), Status.valueOf(e.toStatus), e.version,
                        crypto.decrypt("event", e.communityId, e.id, e.noteCipher, String.class), e.occurredAt)).toList();
        access.audit(actor, id, "BOOKING_HISTORY_VIEW");
        return new PageResponse<>(items, page.page(), page.pageSize(), total);
    }
    /** 返回本人或有效授权老人选项；仅本人绑定可预填档案地址和电话。 */
    @Transactional
    public List<BookingResponses.ElderOption> elderOptions(Authentication auth, PageQuery page) {
        var actor = access.actor(auth);
        return queries.elders(actor, page.offset(), page.pageSize(), Instant.now()).stream().map(row -> {
            var data = elderCrypto.decrypt(row.getId(), row.getCommunityId(), row.getPayloadCipher());
            boolean canBook = "ACTIVE".equals(row.getStatus()) && queries.elderAccess(actor, row.getId(), true, Instant.now()) > 0;
            boolean prefill = "ELDER".equals(actor.getRole()) && canBook;
            access.audit(actor, row.getId(), "BOOKING_ELDER_OPTION");
            return new BookingResponses.ElderOption(row.getId().toString(), data.name(), canBook,
                    prefill ? data.address() : null, prefill ? data.emergencyContactName() : null,
                    prefill ? data.emergencyContactPhone() : null);
        }).toList();
    }
    /** 提交申请；请求编号在账号和社区内幂等，资料、快照和事件同事务保存。 */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public BookingResponses.Detail create(Authentication auth, BookingRequests.Create input) {
        var actor = access.actor(auth); access.lock(actor);
        var app = input.application(); access.elder(actor, app.elderId(), true);
        String hash = crypto.hash("application", app);
        var row = bookings.selectOne(new QueryWrapper<BookingRow>().eq("community_id", actor.getCommunityId())
                .eq("applicant_id", actor.getId()).eq("request_id", input.requestId().toString()));
        if (row != null) {
            if (!row.requestHash.equals(hash)) throw conflict("IDEMPOTENCY_CONFLICT", "该请求编号已用于其他预约内容，请恢复原内容重试或新建申请");
            return detail(actor, row);
        }
        var elder = elders.find(actor.getCommunityId(), app.elderId());
        if (!"ACTIVE".equals(elder.getStatus())) throw conflict("ELDER_ARCHIVED", "老人档案已归档，不能提交新预约");
        var service = catalog.require(actor.getCommunityId(), app.serviceId()); catalog.available(service);
        var snapshot = catalog.view(service);
        rules.start(app.requestedStart()); rules.specific(snapshot, app);
        row = new BookingRow(); row.id = IdWorker.getId(); row.communityId = actor.getCommunityId();
        row.elderId = app.elderId(); row.applicantId = actor.getId(); row.serviceId = app.serviceId();
        row.requestId = input.requestId().toString(); row.requestHash = hash; row.snapshotJson = crypto.encode(snapshot);
        row.payloadCipher = crypto.encrypt("application", row.communityId, row.id, app);
        row.requestedStart = app.requestedStart(); row.status = "PENDING"; row.version = 0L; row.hasException = false;
        row.createdAt = Instant.now(); row.updatedAt = row.createdAt;
        bookings.insert(row); event(actor, row, "CREATE", "PENDING", "已收到预约申请，待社区确认");
        return detail(actor, row);
    }
    /** 确认或改派：锁内检查资格、服务范围和时间冲突，协调说明与安排同时入库。 */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public BookingResponses.Detail arrange(Authentication auth, long id, BookingRequests.Arrange input) {
        var actor = access.operator(auth); access.lock(actor); var row = visible(actor, id); version(row.version, input.version());
        requireState(row, "PENDING", "CONFIRMED");
        if ("CONFIRMED".equals(row.status) && !row.scheduledStart.isAfter(Instant.now()))
            throw conflict("BOOKING_ALREADY_DUE", "原服务时间已到，请处理异常或终止后重新预约");
        catalog.available(catalog.require(row.communityId, row.serviceId));
        var snapshot = snapshot(row); rules.start(input.start());
        if (!catalog.provider(row.communityId, Long.parseLong(snapshot.providerId())).enabled)
            throw conflict("PROVIDER_UNAVAILABLE", "预约原提供方已停用，请取消后选择其他服务");
        if (!input.end().equals(input.start().plusSeconds(snapshot.durationMinutes() * 60L)))
            throw error(HttpStatus.BAD_REQUEST, "INVALID_ARGUMENT", "结束时间必须与预约快照中的服务时长一致");
        rules.arrangement(application(row), snapshot.category(), input.start(), input.end());
        boolean changed = !"PENDING".equals(row.status) || !input.start().equals(row.requestedStart);
        if (changed && input.coordinationNote().isBlank())
            throw error(HttpStatus.BAD_REQUEST, "COORDINATION_REQUIRED", "改期或重新安排须填写与申请人的协调结果");
        WorkerRow worker = workers.selectById(input.workerId()); var account = accounts.selectById(input.workerId());
        if (worker == null || worker.communityId != row.communityId.longValue() || !worker.enabled || account == null
                || !"STAFF".equals(account.getRole()) || !"ACTIVE".equals(account.getStatus())
                || !Objects.equals(account.getCommunityId(), row.communityId))
            throw conflict("WORKER_UNAVAILABLE", "人员当前不可安排，请选择本社区有效服务人员");
        if (!worker.providerId.toString().equals(snapshot.providerId()) || !worker.serviceArea.equals(snapshot.serviceArea())
                || !Arrays.asList(worker.categories.split(",")).contains(snapshot.category().name()))
            throw conflict("WORKER_SCOPE_MISMATCH", "人员提供方、服务范围或可服务类别不符合该预约要求");
        if (snapshot.professional() && (!worker.qualification.equals(snapshot.qualification())
                || worker.qualificationExpiresAt == null || worker.qualificationExpiresAt.isBefore(input.end())))
            throw conflict("WORKER_QUALIFICATION_INVALID", "人员资质不符或将在服务结束前到期");
        if (queries.conflicts(row.communityId, worker.id, row.id, input.start(), input.end()) > 0)
            throw conflict("WORKER_TIME_CONFLICT", "该人员在所选时段已有安排，请更换人员或时间");
        String from = row.status; row.workerId = worker.id; row.scheduledStart = input.start(); row.scheduledEnd = input.end();
        row.status = "CONFIRMED";
        String note = "人员编号：" + worker.id + "；服务时段：" + input.start() + " 至 " + input.end()
                + "；协调结果：" + (input.coordinationNote().isBlank() ? "按申请时间安排" : input.coordinationNote().strip());
        save(actor, row, "PENDING".equals(from) ? "CONFIRM" : "RESCHEDULE", from, note);
        return detail(actor, row);
    }
    /** 执行状态动作；社区、申请方和执行人各自只能执行授权的转换。 */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public BookingResponses.Detail action(Authentication auth, long id, String action, BookingRequests.Action input) {
        var actor = access.actor(auth); access.lock(actor); var row = visible(actor, id); version(row.version, input.version());
        String from = row.status; Instant now = Instant.now();
        switch (action) {
            case "reject" -> { access.requireOperator(actor); requireState(row, "PENDING"); row.status = "REJECTED"; }
            case "cancel" -> {
                requireState(row, "PENDING", "CONFIRMED");
                if (!access.isOperator(actor)) {
                    access.elder(actor, row.elderId, true);
                    if ("CONFIRMED".equals(row.status) && !now.isBefore(row.scheduledStart.minusSeconds(rules.view().cancelBeforeMinutes() * 60L)))
                        throw conflict("CANCELLATION_CLOSED", "已超过取消截止时间，请联系社区处理");
                }
                row.status = "CANCELLED";
            }
            case "start" -> {
                requireWorker(actor, row); requireState(row, "CONFIRMED");
                if (row.hasException) throw conflict("UNRESOLVED_EXCEPTION", "服务异常尚未处理，请先联系社区处理");
                if (bookings.selectCount(new QueryWrapper<BookingRow>().eq("community_id", row.communityId)
                        .eq("worker_id", row.workerId).eq("status", "IN_PROGRESS").ne("id", row.id)) > 0)
                    throw conflict("WORKER_STILL_SERVING", "您仍有正在执行的任务，请先完成或联系社区终止原任务");
                if (now.isBefore(row.scheduledStart) || !now.isBefore(row.scheduledEnd))
                    throw conflict("START_TIME_INVALID", "请在安排时段内开始服务，错过时段请联系社区处理");
                row.startedAt = now; row.status = "IN_PROGRESS";
            }
            case "complete" -> {
                requireWorker(actor, row); requireState(row, "IN_PROGRESS");
                if (row.hasException) throw conflict("UNRESOLVED_EXCEPTION", "服务异常尚未处理，请由社区记录处理结果后再完成");
                row.completedAt = now; row.status = "COMPLETED";
                row.resultCipher = crypto.encrypt("result", row.communityId, row.id, input.note().strip());
            }
            case "exception" -> {
                requireWorker(actor, row); requireState(row, "CONFIRMED", "IN_PROGRESS"); row.hasException = true;
            }
            case "resolve" -> {
                access.requireOperator(actor); requireState(row, "CONFIRMED", "IN_PROGRESS");
                if (!row.hasException) throw conflict("NO_OPEN_EXCEPTION", "当前预约没有待处理异常");
                row.hasException = false;
            }
            case "terminate" -> {
                access.requireOperator(actor); requireState(row, "CONFIRMED", "IN_PROGRESS");
                row.status = "TERMINATED"; row.completedAt = now; row.hasException = false;
                row.resultCipher = crypto.encrypt("result", row.communityId, row.id, input.note().strip());
            }
            default -> throw missing();
        }
        save(actor, row, action.toUpperCase(Locale.ROOT), from, input.note().strip());
        return detail(actor, row);
    }
    /** 检查执行角色及本人任务关系，管理员也不能代替服务人员提交执行结果。 */
    private void requireWorker(UserAccount actor, BookingRow row) {
        if (!"STAFF".equals(actor.getRole()) || !Objects.equals(row.workerId, actor.getId()))
            throw error(HttpStatus.FORBIDDEN, "FORBIDDEN", "仅被安排的服务人员可执行此操作");
    }
    /** 校验来源状态，不允许覆盖已结束预约。 */
    private void requireState(BookingRow row, String... states) {
        if (!Arrays.asList(states).contains(row.status)) throw conflict("BOOKING_STATE_CONFLICT", "当前预约状态不允许此操作，请刷新后重试");
    }
    /** 在持有社区锁的事务内保存状态、版本和事件。 */
    private void save(UserAccount actor, BookingRow row, String action, String from, String note) {
        row.version++; row.updatedAt = Instant.now();
        bookings.updateById(row); event(actor, row, action, from, note);
    }
    /** 记录不可修改的关键状态、安排和加密说明。 */
    private void event(UserAccount actor, BookingRow row, String action, String from, String note) {
        var e = new EventRow(); e.id = IdWorker.getId(); e.communityId = row.communityId; e.bookingId = row.id;
        e.actorId = actor.getId(); e.action = action; e.fromStatus = from; e.toStatus = row.status; e.version = row.version;
        e.occurredAt = Instant.now(); e.noteCipher = crypto.encrypt("event", e.communityId, e.id, note); events.insert(e);
    }
    /** 对单笔预约强制执行社区和对象权限。 */
    private BookingRow visible(UserAccount actor, long id) {
        var row = queries.visible(actor, id, Instant.now()); if (row == null) throw missing(); return row;
    }
    /** 恢复提交时的项目和价格快照，后续目录变更不影响历史。 */
    private BookingResponses.Catalog snapshot(BookingRow row) { return crypto.decode(row.snapshotJson, BookingResponses.Catalog.class); }
    /** 仅在对象鉴权后解密必要的预约资料。 */
    private BookingRequests.Application application(BookingRow row) { return crypto.decrypt("application", row.communityId, row.id, row.payloadCipher, BookingRequests.Application.class); }
    /** 列表不解密地址、电话或注意事项。 */
    private BookingResponses.Summary summary(BookingRow row) {
        var s = snapshot(row);
        return new BookingResponses.Summary(row.id.toString(), row.elderId.toString(), s.name(), s.category(),
                Status.valueOf(row.status), row.requestedStart, row.scheduledStart, row.scheduledEnd, row.hasException, row.version);
    }
    /** 结束任务不再向工作人员提供地址、联系人和服务注意事项。 */
    private BookingResponses.Detail detail(UserAccount actor, BookingRow row) {
        boolean hide = "STAFF".equals(actor.getRole()) && !Set.of("CONFIRMED","IN_PROGRESS").contains(row.status);
        var worker = row.workerId == null ? null : accounts.selectById(row.workerId);
        return new BookingResponses.Detail(row.id.toString(), row.elderId.toString(), row.applicantId.toString(), snapshot(row),
                hide ? null : application(row), Status.valueOf(row.status), row.workerId == null ? null : row.workerId.toString(),
                worker == null ? null : worker.getDisplayName(), row.scheduledStart, row.scheduledEnd, row.startedAt, row.completedAt,
                row.hasException, row.resultCipher == null ? null : crypto.decrypt("result", row.communityId, row.id, row.resultCipher, String.class),
                row.version, row.createdAt, row.updatedAt);
    }
}
