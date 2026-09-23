package com.salarytracker.ai.trace;

import com.salarytracker.ai.model.AiModelConnectionService;
import com.salarytracker.ai.session.AgentTurnRepository;
import com.salarytracker.identity.CurrentUserResolver;
import com.salarytracker.platform.ai.LlmGateway;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

@Service
public class AgentTraceService {
    private static final BigDecimal MILLION = new BigDecimal("1000000");
    private final JdbcTemplate jdbc;
    private final CurrentUserResolver currentUser;

    public AgentTraceService(JdbcTemplate jdbc, CurrentUserResolver currentUser) {
        this.jdbc = jdbc;
        this.currentUser = currentUser;
    }

    @Transactional
    public void persist(AgentTurnRepository.AgentTurnView turn,
                        AiModelConnectionService.RuntimeConnection model,
                        LlmGateway.ChatResponse response) {
        bindModel(turn, model);
        persistExecutions(turn, model, response);
    }

    public void bindModel(AgentTurnRepository.AgentTurnView turn,
                          AiModelConnectionService.RuntimeConnection model) {
        jdbc.update("""
                UPDATE agent_turn SET model_connection_id=?,provider_type=?,provider_base_host=?,model_name=?,model_config_revision=?
                WHERE id=? AND user_id=?
                """, AiModelConnectionService.ENVIRONMENT_ID.equals(model.id()) ? null : model.id(),
                model.providerType(), model.baseHost(), model.modelName(), model.revision(), turn.id(), turn.userId());
    }

    private void persistExecutions(AgentTurnRepository.AgentTurnView turn,
                                   AiModelConnectionService.RuntimeConnection model,
                                   LlmGateway.ChatResponse response) {
        for (LlmGateway.ModelExecution execution : response.modelExecutions()) {
            Cost cost = cost(execution.usage(), model.pricing());
            jdbc.update("""
                    INSERT INTO ai_usage(turn_id,session_id,user_id,model_connection_id,provider_type,model_name,round_no,
                      input_tokens,output_tokens,cache_hit_tokens,cache_miss_tokens,reasoning_tokens,total_tokens,
                      first_token_ms,duration_ms,pricing_version,currency,input_unit_price,output_unit_price,
                      cache_hit_unit_price,cache_miss_unit_price,reasoning_unit_price,estimated_input_cost,
                      estimated_output_cost,estimated_cache_cost,estimated_reasoning_cost,estimated_total_cost)
                    VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
                    ON DUPLICATE KEY UPDATE duration_ms=VALUES(duration_ms),first_token_ms=VALUES(first_token_ms)
                    """, turn.id(), turn.sessionId(), turn.userId(),
                    AiModelConnectionService.ENVIRONMENT_ID.equals(model.id()) ? null : model.id(),
                    model.providerType(), model.modelName(), execution.round(), execution.usage().inputTokens(),
                    execution.usage().outputTokens(), execution.usage().cacheHitTokens(),
                    execution.usage().cacheMissTokens(), execution.usage().reasoningTokens(),
                    execution.usage().totalTokens(), execution.firstTokenMs(), execution.durationMs(),
                    model.pricing() == null ? null : model.pricing().version(), cost.currency(), cost.inputUnit(),
                    cost.outputUnit(), cost.cacheHitUnit(), cost.cacheMissUnit(), cost.reasoningUnit(), cost.input(),
                    cost.output(), cost.cache(), cost.reasoning(), cost.total());
        }
        int sequence = 0;
        for (LlmGateway.ToolExecution execution : response.toolExecutions()) {
            jdbc.update("""
                    INSERT INTO agent_tool_call(turn_id,session_id,user_id,sequence_no,tool_name,status,result_summary,duration_ms)
                    VALUES(?,?,?,?,?,?,?,?)
                    ON DUPLICATE KEY UPDATE status=VALUES(status),result_summary=VALUES(result_summary),duration_ms=VALUES(duration_ms)
                    """, turn.id(), turn.sessionId(), turn.userId(), ++sequence, execution.name(), execution.status(),
                    trim(execution.summary(), 1000), execution.durationMs());
        }
    }

    public TraceView get(String turnId) {
        long userId = currentUser.id();
        TurnHeader turn = jdbc.query("""
                SELECT id,status,provider_type,model_name FROM agent_turn WHERE id=? AND user_id=?
                """, (r, n) -> new TurnHeader(r.getString("id"), r.getString("status"),
                r.getString("provider_type"), r.getString("model_name")), turnId, userId).stream().findFirst()
                .orElseThrow(() -> new IllegalArgumentException("turn 不存在"));
        List<ModelUsageView> usage = jdbc.query("""
                SELECT * FROM ai_usage WHERE turn_id=? AND user_id=? ORDER BY round_no
                """, (r, n) -> new ModelUsageView(r.getInt("round_no"), r.getLong("duration_ms"),
                r.getLong("first_token_ms"), r.getLong("input_tokens"), r.getLong("output_tokens"),
                r.getLong("cache_hit_tokens"), r.getLong("cache_miss_tokens"), r.getLong("reasoning_tokens"),
                r.getLong("total_tokens"), r.getString("currency"), r.getBigDecimal("estimated_total_cost")),
                turnId, userId);
        List<ToolView> tools = jdbc.query("""
                SELECT sequence_no,tool_name,status,result_summary,duration_ms FROM agent_tool_call
                WHERE turn_id=? AND user_id=? ORDER BY sequence_no
                """, (r, n) -> new ToolView(r.getInt("sequence_no"), r.getString("tool_name"),
                r.getString("status"), r.getString("result_summary"), r.getLong("duration_ms")), turnId, userId);
        long tokens = usage.stream().mapToLong(ModelUsageView::totalTokens).sum();
        long duration = usage.stream().mapToLong(ModelUsageView::durationMs).sum()
                + tools.stream().mapToLong(ToolView::durationMs).sum();
        long firstToken = usage.stream().mapToLong(ModelUsageView::firstTokenMs).filter(value -> value > 0)
                .findFirst().orElse(0);
        String currency = usage.stream().map(ModelUsageView::currency).filter(value -> value != null).findFirst().orElse(null);
        BigDecimal estimatedCost = currency == null ? null : usage.stream().map(ModelUsageView::estimatedCost)
                .filter(value -> value != null).reduce(BigDecimal.ZERO, BigDecimal::add);
        return new TraceView(turn.id(), turn.status(), turn.providerType(), turn.modelName(), duration, firstToken,
                tokens, currency, estimatedCost, usage, tools);
    }

    private Cost cost(LlmGateway.TokenUsage usage, AiModelConnectionService.Pricing pricing) {
        if (pricing == null) return Cost.none();
        long input = Math.max(0, usage.inputTokens() - usage.cacheHitTokens() - usage.cacheMissTokens());
        long output = Math.max(0, usage.outputTokens() - usage.reasoningTokens());
        BigDecimal inputCost = amount(input, pricing.inputPerMillion());
        BigDecimal outputCost = amount(output, pricing.outputPerMillion());
        BigDecimal cacheCost = amount(usage.cacheHitTokens(), pricing.cacheHitPerMillion())
                .add(amount(usage.cacheMissTokens(), pricing.cacheMissPerMillion()));
        BigDecimal reasoningCost = amount(usage.reasoningTokens(), pricing.reasoningPerMillion());
        return new Cost(pricing.currency(), pricing.inputPerMillion(), pricing.outputPerMillion(),
                pricing.cacheHitPerMillion(), pricing.cacheMissPerMillion(), pricing.reasoningPerMillion(),
                inputCost, outputCost, cacheCost, reasoningCost,
                inputCost.add(outputCost).add(cacheCost).add(reasoningCost));
    }

    private BigDecimal amount(long tokens, BigDecimal price) {
        return price.multiply(BigDecimal.valueOf(tokens)).divide(MILLION, 12, RoundingMode.HALF_UP);
    }

    private String trim(String value, int max) {
        return value == null ? null : value.substring(0, Math.min(value.length(), max));
    }

    private record TurnHeader(String id, String status, String providerType, String modelName) { }
    private record Cost(String currency, BigDecimal inputUnit, BigDecimal outputUnit,
                        BigDecimal cacheHitUnit, BigDecimal cacheMissUnit, BigDecimal reasoningUnit,
                        BigDecimal input, BigDecimal output, BigDecimal cache, BigDecimal reasoning,
                        BigDecimal total) {
        private static Cost none() { return new Cost(null, null, null, null, null, null, null, null, null, null, null); }
    }

    public record ModelUsageView(int round, long durationMs, long firstTokenMs, long inputTokens,
                                 long outputTokens, long cacheHitTokens, long cacheMissTokens,
                                 long reasoningTokens, long totalTokens, String currency,
                                 BigDecimal estimatedCost) { }
    public record ToolView(int sequence, String name, String status, String summary, long durationMs) { }
    public record TraceView(String turnId, String status, String providerType, String modelName,
                            long totalDurationMs, long firstTokenMs, long totalTokens, String currency,
                            BigDecimal estimatedCost, List<ModelUsageView> modelExecutions,
                            List<ToolView> toolExecutions) { }
}
