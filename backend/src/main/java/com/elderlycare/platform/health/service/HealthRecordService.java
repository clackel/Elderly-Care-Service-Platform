package com.elderlycare.platform.health.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.elderlycare.platform.common.api.*;
import com.elderlycare.platform.health.api.*;
import com.elderlycare.platform.health.domain.*;
import com.elderlycare.platform.health.domain.HealthTypes.*;
import com.elderlycare.platform.health.mapper.*;
import com.elderlycare.platform.identity.domain.UserAccount;
import com.elderlycare.platform.elder.service.ElderCrypto;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import java.time.Instant;
import java.util.*;
import static com.elderlycare.platform.health.service.HealthAccess.*;

/** 健康记录事务和只追加版本，权限不依赖预约服务。 */
@Service
@Transactional(isolation = Isolation.READ_COMMITTED)
public class HealthRecordService {
    private final HealthAccess access;
    private final HealthQueries queries;
    private final HealthRecordMapper records;
    private final HealthRevisionMapper revisions;
    private final HealthCrypto crypto;
    private final HealthRules rules;
    private final HealthMutations mutations;
    private final ElderCrypto elderCrypto;
    /** 装配健康规则、对象查询、密文和同事务幂等组件。 */
    public HealthRecordService(HealthAccess access, HealthQueries queries, HealthRecordMapper records,
            HealthRevisionMapper revisions, HealthCrypto crypto, HealthRules rules, HealthMutations mutations, ElderCrypto elderCrypto) {
        this.access = access; this.queries = queries; this.records = records; this.revisions = revisions;
        this.crypto = crypto; this.rules = rules; this.mutations = mutations; this.elderCrypto = elderCrypto;
    }
    /** 分页返回当前可访问老人及服务端动作能力；只解密选中页的姓名。 */
    public PageResponse<HealthResponses.Elder> elders(Authentication auth, PageQuery page) {
        var a = access.actor(auth); var now = Instant.now();
        long total = queries.elderCount(a, now);
        var items = queries.elders(a, now, page.offset(), page.pageSize()).stream().map(e -> {
            boolean active = "ACTIVE".equals(e.getStatus()), owner = "ELDER".equals(a.getRole());
            var data = elderCrypto.decrypt(e.getId(), e.getCommunityId(), e.getPayloadCipher());
            return new HealthResponses.Elder(e.getId().toString(), data.name(), !active,
                    active && !"FAMILY".equals(a.getRole()), !"COMMUNITY_OPERATOR".equals(a.getRole()), owner && active);
        }).toList();
        access.audit(a, a.getId(), "ELDERS_READ");
        return new PageResponse<>(items, page.page(), page.pageSize(), total);
    }
    /** 列表共用详情权限；默认有效记录，本人及原代录人员可另查作废记录。 */
    public PageResponse<HealthResponses.Record> list(Authentication auth, long elderId, PageQuery page,
            Type type, Status status, Instant from, Instant to) {
        var a = access.actor(auth); var e = access.elder(a, elderId); rules.range(from, to);
        var now = Instant.now(); String state = status == null ? "ACTIVE" : status.name();
        long total = queries.recordCount(a, elderId, name(type), state, null, from, to, now);
        var items = queries.records(a, elderId, name(type), state, null, from, to, now, page.offset(), page.pageSize(), false)
                .stream().map(r -> view(a, r, "ACTIVE".equals(e.getStatus()))).toList();
        access.audit(a, elderId, "RECORDS_READ");
        return new PageResponse<>(items, page.page(), page.pageSize(), total);
    }
    /** 返回当前版本；更正原因和历史旧值仅通过本人的历史接口读取。 */
    public HealthResponses.Record detail(Authentication auth, long id) {
        var a = access.actor(auth); var row = access.record(a, id); var elder = access.elder(a, row.elderId);
        var result = view(a, row, "ACTIVE".equals(elder.getStatus())); access.audit(a, id, "RECORD_READ"); return result;
    }
    /** 创建前先核验写权限，重试也重新校验原结果是否仍可见。 */
    public HealthResponses.Mutation create(Authentication auth, long elderId, HealthRequests.Create input) {
        var a = access.writer(auth); access.writable(a, access.elder(a, elderId));
        var m = rules.normalize(input.measurement());
        String hash = mutations.hash(a, "CREATE", List.of(elderId, m));
        var replay = mutations.replay(a, "CREATE", input.requestId(), hash);
        if (replay != null) { access.record(a, Long.parseLong(replay.id())); return replay; }
        var row = new HealthRecordRow(); row.id = IdWorker.getId(); row.communityId = a.getCommunityId(); row.elderId = elderId;
        row.type = m.type().name(); row.originalActorId = a.getId();
        row.entryMode = "ELDER".equals(a.getRole()) ? "SELF" : "COMMUNITY_ASSIST";
        row.ownerTakenOver = false; row.version = 1L; row.status = "ACTIVE"; row.measuredAt = m.measuredAt();
        row.glucoseScene = name(m.glucoseScene()); row.createdAt = Instant.now(); row.updatedAt = row.createdAt;
        records.insert(row); append(a, row, new HealthResponses.Payload(m, ""), "CREATE");
        return mutations.save(a, "CREATE", input.requestId(), hash, row.id, row.version);
    }
    /** 更正按期望版本追加；老人更正社区代录后永久取得该记录维护权。 */
    public HealthResponses.Mutation correct(Authentication auth, long id, HealthRequests.Correct input) {
        var a = access.writer(auth); var row = access.record(a, id); access.writable(a, access.elder(a, row.elderId));
        var m = rules.normalize(input.measurement()); String reason = rules.reason(input.reason());
        String hash = mutations.hash(a, "CORRECT", List.of(id, input.version(), m, reason));
        var replay = mutations.replay(a, "CORRECT", input.requestId(), hash); if (replay != null) return replay;
        editable(row, input.version());
        if (!row.type.equals(m.type().name())) throw conflict("HEALTH_TYPE_IMMUTABLE", "不能更改指标类型，请作废原记录后新建");
        row.version++; row.measuredAt = m.measuredAt(); row.glucoseScene = name(m.glucoseScene());
        advance(a, row, input.version()); append(a, row, new HealthResponses.Payload(m, reason), "CORRECT");
        return mutations.save(a, "CORRECT", input.requestId(), hash, id, row.version);
    }
    /** 作废保留最新测量及原因密文，不删除历史，不支持恢复。 */
    public HealthResponses.Mutation voidRecord(Authentication auth, long id, HealthRequests.VoidRecord input) {
        var a = access.writer(auth); var row = access.record(a, id); access.writable(a, access.elder(a, row.elderId));
        String reason = rules.reason(input.reason());
        String hash = mutations.hash(a, "VOID", List.of(id, input.version(), reason));
        var replay = mutations.replay(a, "VOID", input.requestId(), hash); if (replay != null) return replay;
        editable(row, input.version()); var measurement = payload(row, row.version).measurement();
        row.version++; row.status = "VOID"; advance(a, row, input.version());
        append(a, row, new HealthResponses.Payload(measurement, reason), "VOID");
        return mutations.save(a, "VOID", input.requestId(), hash, id, row.version);
    }
    /** 历史版本仅允许有效绑定本人分页读取，包括作废记录。 */
    public PageResponse<HealthResponses.Revision> history(Authentication auth, long id, PageQuery page) {
        var a = access.actor(auth); var row = access.record(a, id); access.owner(a, row.elderId);
        var q = new QueryWrapper<HealthRevisionRow>().eq("community_id", a.getCommunityId()).eq("record_id", id);
        long total = revisions.selectCount(q);
        var items = revisions.selectList(q.orderByDesc("version").last("LIMIT " + page.pageSize() + " OFFSET " + page.offset()))
                .stream().map(r -> {
                    var value = crypto.decrypt(row.communityId, row.elderId, row.id, r.version, r.payloadCipher);
                    return new HealthResponses.Revision(r.version, value.measurement(), value.reason(), r.actorId.toString(), r.action, r.occurredAt);
                }).toList();
        access.audit(a, id, "REVISIONS_READ");
        return new PageResponse<>(items, page.page(), page.pageSize(), total);
    }
    /** 原始有效点按时间排序，超过2000点明确拒绝，血糖不混合场景。 */
    public List<HealthResponses.Record> trends(Authentication auth, long elderId, Type type, Scene scene, Instant from, Instant to) {
        var a = access.actor(auth); access.trend(a); var elder = access.elder(a, elderId); rules.trend(type, scene, from, to);
        var rows = queries.records(a, elderId, type.name(), "ACTIVE", name(scene), from, to,
                Instant.now(), 0, HealthRules.TREND_LIMIT + 1, true);
        if (rows.size() > HealthRules.TREND_LIMIT)
            throw error(org.springframework.http.HttpStatus.BAD_REQUEST, "HEALTH_TREND_TOO_LARGE", "测量点过多，请缩小日期范围");
        var result = rows.stream().map(r -> view(a, r, "ACTIVE".equals(elder.getStatus()))).toList();
        access.audit(a, elderId, "TREND_READ"); return result;
    }
    /** 状态与版本同时约束更正或作废，终态不可继续编辑。 */
    private void editable(HealthRecordRow row, long expected) {
        version(row.version, expected);
        if (!"ACTIVE".equals(row.status)) throw conflict("HEALTH_RECORD_VOID", "记录已作废，请重新录入");
    }
    /** 持有社区锁时推进版本；原始录入人及方式永不改变。 */
    private void advance(UserAccount actor, HealthRecordRow row, long expected) {
        if ("ELDER".equals(actor.getRole()) && "COMMUNITY_ASSIST".equals(row.entryMode)) row.ownerTakenOver = true;
        row.updatedAt = Instant.now();
        if (queries.advance(row, expected) != 1) throw conflict("VERSION_CONFLICT", "记录已更新，请刷新后重新操作");
    }
    /** 追加不可覆盖的密文版本，并与当前指针和请求编号同事务审计。 */
    private void append(UserAccount a, HealthRecordRow row, HealthResponses.Payload value, String action) {
        var r = new HealthRevisionRow(); r.id = IdWorker.getId(); r.communityId = row.communityId; r.recordId = row.id;
        r.version = row.version; r.actorId = a.getId(); r.action = action; r.occurredAt = row.updatedAt;
        r.payloadCipher = crypto.encrypt(row.communityId, row.elderId, row.id, row.version, value);
        revisions.insert(r); access.audit(a, row.id, action);
    }
    /** 只按已核验对象及版本读取密文，不全表解密。 */
    private HealthResponses.Payload payload(HealthRecordRow row, long version) {
        var r = revisions.selectOne(new QueryWrapper<HealthRevisionRow>().eq("community_id", row.communityId)
                .eq("record_id", row.id).eq("version", version));
        if (r == null) throw new IllegalStateException("健康记录版本不完整");
        return crypto.decrypt(row.communityId, row.elderId, row.id, version, r.payloadCipher);
    }
    /** 响应返回当前测量及来源；历史原因不会通过家属或代录详情泄露。 */
    private HealthResponses.Record view(UserAccount a, HealthRecordRow row, boolean activeElder) {
        return new HealthResponses.Record(row.id.toString(), row.elderId.toString(), payload(row, row.version).measurement(),
                rules.unit(Type.valueOf(row.type)), row.status, row.version, row.originalActorId.toString(),
                row.entryMode, row.ownerTakenOver, row.version > 1, row.createdAt, row.updatedAt,
                activeElder && "ACTIVE".equals(row.status) && !"FAMILY".equals(a.getRole()), "ELDER".equals(a.getRole()));
    }
    /** 将可选枚举转为查询参数，保留空筛选。 */
    private static String name(Enum<?> value) { return value == null ? null : value.name(); }
}

