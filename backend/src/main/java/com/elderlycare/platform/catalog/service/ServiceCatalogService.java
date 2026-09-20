package com.elderlycare.platform.catalog.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.elderlycare.platform.booking.api.*;
import com.elderlycare.platform.booking.domain.*;
import com.elderlycare.platform.booking.domain.BookingTypes.Category;
import com.elderlycare.platform.booking.mapper.*;
import com.elderlycare.platform.booking.service.*;
import com.elderlycare.platform.common.api.*;
import com.elderlycare.platform.identity.mapper.UserAccountMapper;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import java.util.*;
import static com.elderlycare.platform.booking.service.BookingAccess.*;

@Service
public class ServiceCatalogService {
    private final BookingAccess access;
    private final BookingCrypto crypto;
    private final ServiceMapper services;
    private final ProviderMapper providers;
    private final WorkerMapper workers;
    private final BookingQueries queries;
    private final UserAccountMapper accounts;

    /** 装配目录、提供方、人员与数据库并发保护依赖。 */
    public ServiceCatalogService(BookingAccess access, BookingCrypto crypto, ServiceMapper services,
            ProviderMapper providers, WorkerMapper workers, BookingQueries queries, UserAccountMapper accounts) {
        this.access = access; this.crypto = crypto; this.services = services; this.providers = providers;
        this.workers = workers; this.queries = queries; this.accounts = accounts;
    }

    /** 分页查看本社区目录；普通用户只能看到上架且提供方有效的项目。 */
    @Transactional(readOnly = true)
    public PageResponse<BookingResponses.Catalog> list(Authentication auth, PageQuery page, Category category, boolean available) {
        var actor = access.actor(auth);
        var q = new QueryWrapper<ServiceRow>().eq("community_id", actor.getCommunityId());
        if (category != null) q.eq("category", category.name());
        if (available || !access.isOperator(actor)) q.eq("enabled", true).inSql("provider_id",
                "SELECT id FROM service_provider WHERE enabled = TRUE");
        long total = services.selectCount(q);
        var rows = services.selectList(q.orderByDesc("id").last("LIMIT " + page.pageSize() + " OFFSET " + page.offset()));
        return new PageResponse<>(rows.stream().map(this::view).toList(), page.page(), page.pageSize(), total);
    }

    /** 查看当前社区项目详情，未上架项目只允许管理人员读取。 */
    public BookingResponses.Catalog detail(Authentication auth, long id) {
        var actor = access.actor(auth);
        var row = require(actor.getCommunityId(), id);
        if (!access.isOperator(actor)) available(row);
        return view(row);
    }

    /** 创建或按版本替换项目；保留历史预约快照，创建编号重复时核验内容一致。 */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public BookingResponses.Catalog save(Authentication auth, Long id, BookingRequests.Catalog input) {
        var actor = access.operator(auth); access.lock(actor);
        String hash = crypto.hash("catalog", input);
        ServiceRow row;
        if (id == null) {
            version(0, input.version());
            row = services.selectOne(new QueryWrapper<ServiceRow>().eq("community_id", actor.getCommunityId()).eq("request_id", input.requestId().toString()));
            if (row != null) {
                if (!row.requestHash.equals(hash)) throw conflict("IDEMPOTENCY_CONFLICT", "请求编号已用于其他服务项目内容");
                return view(row);
            }
            row = new ServiceRow(); row.id = IdWorker.getId(); row.communityId = actor.getCommunityId();
            row.version = 0L; row.requestId = input.requestId().toString(); row.requestHash = hash;
        } else { row = require(actor.getCommunityId(), id); version(row.version, input.version()); row.version++; }
        var provider = provider(actor.getCommunityId(), input.providerId());
        if (input.enabled() && !provider.enabled) throw conflict("PROVIDER_UNAVAILABLE", "提供方已停用，请先恢复提供方");
        if (input.professional() && (input.qualification().isBlank() || !Set.of(Category.CARE, Category.REHABILITATION).contains(input.category())))
            throw error(HttpStatus.BAD_REQUEST, "INVALID_ARGUMENT", "专业服务仅适用于护理或康复，且必须填写人员资质要求");
        if (!input.professional() && !input.qualification().isBlank())
            throw error(HttpStatus.BAD_REQUEST, "INVALID_ARGUMENT", "生活服务请清空专业资质要求");
        row.category = input.category().name(); row.name = input.name().strip(); row.description = input.description().strip();
        row.durationMinutes = input.durationMinutes(); row.priceFen = input.priceFen(); row.serviceArea = input.serviceArea().strip();
        row.providerId = input.providerId(); row.professional = input.professional(); row.qualification = input.qualification().strip(); row.enabled = input.enabled();
        if (id == null) services.insert(row); else services.updateById(row);
        access.audit(actor, row.id, "CATALOG_SAVE");
        return view(row);
    }

    /** 分页维护本社区提供方，禁用后停止受理新预约及安排。 */
    public PageResponse<BookingResponses.Provider> providers(Authentication auth, PageQuery page) {
        var actor = access.operator(auth);
        var q = new QueryWrapper<ProviderRow>().eq("community_id", actor.getCommunityId());
        long total = providers.selectCount(q);
        var rows = providers.selectList(q.orderByDesc("id").last("LIMIT " + page.pageSize() + " OFFSET " + page.offset()));
        return new PageResponse<>(rows.stream().map(this::providerView).toList(), page.page(), page.pageSize(), total);
    }

    /** 幂等创建或按版本修改提供方，变更限制在当前社区。 */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public BookingResponses.Provider saveProvider(Authentication auth, Long id, BookingRequests.Provider input) {
        var actor = access.operator(auth); access.lock(actor);
        var hash = crypto.hash("provider", input);
        ProviderRow row;
        if (id == null) {
            version(0, input.version());
            row = providers.selectOne(new QueryWrapper<ProviderRow>().eq("community_id", actor.getCommunityId()).eq("request_id", input.requestId().toString()));
            if (row != null) {
                if (!row.requestHash.equals(hash)) throw conflict("IDEMPOTENCY_CONFLICT", "请求编号已用于其他提供方内容");
                return providerView(row);
            }
            row = new ProviderRow(); row.id = IdWorker.getId(); row.communityId = actor.getCommunityId();
            row.requestId = input.requestId().toString(); row.requestHash = hash; row.version = 0L;
        } else { row = provider(actor.getCommunityId(), id); version(row.version, input.version()); row.version++; }
        row.name = input.name().strip(); row.enabled = input.enabled();
        if (id == null) providers.insert(row); else providers.updateById(row);
        access.audit(actor, row.id, "PROVIDER_SAVE");
        return providerView(row);
    }

    /** 分页读取已核验工作人员的可安排信息。 */
    public PageResponse<BookingResponses.Worker> workers(Authentication auth, PageQuery page) {
        var actor = access.operator(auth);
        var q = new QueryWrapper<WorkerRow>().eq("community_id", actor.getCommunityId());
        long total = workers.selectCount(q);
        var rows = workers.selectList(q.orderByDesc("id").last("LIMIT " + page.pageSize() + " OFFSET " + page.offset()));
        return new PageResponse<>(rows.stream().map(this::workerView).toList(), page.page(), page.pageSize(), total);
    }

    /** 维护服务人员能力；已有未结束安排时拒绝修改，须先改派或终止。 */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public BookingResponses.Worker saveWorker(Authentication auth, long id, BookingRequests.Worker input) {
        var actor = access.operator(auth); access.lock(actor);
        var account = accounts.selectById(id);
        if (account == null || !Objects.equals(account.getCommunityId(), actor.getCommunityId())
                || !"STAFF".equals(account.getRole()) || !"ACTIVE".equals(account.getStatus())) throw missing();
        var p = provider(actor.getCommunityId(), input.providerId());
        if (input.enabled() && !p.enabled) throw conflict("PROVIDER_UNAVAILABLE", "提供方已停用");
        if (!input.qualification().isBlank() && input.qualificationExpiresAt() == null)
            throw error(HttpStatus.BAD_REQUEST, "INVALID_ARGUMENT", "填写人员资质时必须提供资质有效期");
        WorkerRow row = workers.selectById(id);
        boolean create = row == null;
        if (!create) {
            version(row.version, input.version());
            if (queries.openForWorker(actor.getCommunityId(), id) > 0) throw conflict("WORKER_HAS_BOOKINGS", "人员仍有未结束安排，请先改派或终止后修改");
        } else {
            version(0, input.version()); row = new WorkerRow(); row.id = id; row.communityId = actor.getCommunityId(); row.version = -1L;
        }
        row.version++; row.providerId = input.providerId(); row.categories = String.join(",", input.categories().stream().map(Enum::name).sorted().toList());
        row.serviceArea = input.serviceArea().strip(); row.qualification = input.qualification().strip();
        row.qualificationExpiresAt = input.qualificationExpiresAt(); row.enabled = input.enabled();
        if (create) workers.insert(row); else workers.updateById(row);
        access.audit(actor, id, "WORKER_SAVE");
        return workerView(row);
    }

    /** 读取同社区项目，跨社区与不存在使用相同结果。 */
    public ServiceRow require(long community, long id) {
        var row = services.selectById(id);
        if (row == null || row.communityId != community) throw missing();
        return row;
    }
    /** 核验项目及提供方当前是否可接受预约。 */
    public void available(ServiceRow row) {
        if (!row.enabled || !provider(row.communityId, row.providerId).enabled)
            throw conflict("SERVICE_UNAVAILABLE", "服务或提供方已停用，请选择其他服务");
    }
    /** 读取限定社区的提供方。 */
    public ProviderRow provider(long community, long id) {
        var row = providers.selectById(id);
        if (row == null || row.communityId != community) throw missing();
        return row;
    }
    /** 构建可保存到历史预约的完整目录快照。 */
    public BookingResponses.Catalog view(ServiceRow row) {
        return new BookingResponses.Catalog(row.id.toString(), Category.valueOf(row.category), row.name, row.description,
                row.durationMinutes, row.priceFen, row.serviceArea, row.providerId.toString(),
                provider(row.communityId, row.providerId).name, row.professional, row.qualification, row.enabled, row.version);
    }
    /** 生成提供方响应，不输出创建幂等摘要。 */
    private BookingResponses.Provider providerView(ProviderRow row) { return new BookingResponses.Provider(row.id.toString(), row.name, row.enabled, row.version); }
    /** 生成人员能力响应，账号名称只用于社区管理。 */
    private BookingResponses.Worker workerView(WorkerRow row) {
        var account = accounts.selectById(row.id);
        var categories = new HashSet<Category>();
        for (var value : row.categories.split(",")) categories.add(Category.valueOf(value));
        return new BookingResponses.Worker(row.id.toString(), account.getDisplayName(), row.providerId.toString(),
                categories, row.serviceArea, row.qualification, row.qualificationExpiresAt, row.enabled, row.version);
    }
}
