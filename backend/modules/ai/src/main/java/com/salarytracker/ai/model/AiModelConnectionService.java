package com.salarytracker.ai.model;

import com.salarytracker.identity.CurrentUserResolver;
import com.salarytracker.platform.ai.LlmGateway;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.net.URI;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class AiModelConnectionService {
    public static final String ENVIRONMENT_ID = "system-environment";
    private final JdbcTemplate jdbc;
    private final CurrentUserResolver currentUser;
    private final AiCredentialCipher cipher;
    private final ModelEndpointPolicy endpointPolicy;
    private final LlmGateway gateway;
    private final String environmentEndpoint;
    private final String environmentModel;
    private final String environmentKey;

    public AiModelConnectionService(JdbcTemplate jdbc, CurrentUserResolver currentUser, AiCredentialCipher cipher,
                                    ModelEndpointPolicy endpointPolicy, LlmGateway gateway,
                                    @Value("${app.ai.endpoint:}") String environmentEndpoint,
                                    @Value("${app.ai.model:}") String environmentModel,
                                    @Value("${app.ai.api-key:}") String environmentKey) {
        this.jdbc = jdbc;
        this.currentUser = currentUser;
        this.cipher = cipher;
        this.endpointPolicy = endpointPolicy;
        this.gateway = gateway;
        this.environmentEndpoint = environmentEndpoint;
        this.environmentModel = environmentModel;
        this.environmentKey = environmentKey;
    }

    public List<ConnectionView> list() {
        List<ConnectionView> rows = jdbc.query("""
                SELECT * FROM ai_model_connection WHERE user_id=? AND deleted_at IS NULL
                ORDER BY is_default DESC, updated_at DESC
                """, (r, n) -> view(r), currentUser.id());
        if (environmentConfigured()) rows.add(environmentView(currentUser.id()));
        return List.copyOf(rows);
    }

    public ConnectionView get(String id) {
        if (ENVIRONMENT_ID.equals(id) && environmentConfigured()) return environmentView(currentUser.id());
        return owned(id, currentUser.id());
    }

    @Transactional
    public ConnectionView create(ConnectionCommand command) {
        long userId = currentUser.id();
        validate(command, true);
        String id = UUID.randomUUID().toString();
        URI endpoint = endpointPolicy.validate(command.baseUrl());
        String apiKey = required(command.apiKey(), "API Key 不能为空");
        if (Boolean.TRUE.equals(command.isDefault())) clearDefault(userId);
        jdbc.update("""
                INSERT INTO ai_model_connection(id,user_id,display_name,provider_type,base_url,model_name,
                  encrypted_api_key,api_key_fingerprint,api_key_last_four,encryption_key_version,is_default,enabled,
                  timeout_ms,max_output_tokens,temperature)
                VALUES(?,?,?,?,?,?,?,?,?,'v1',?,?,?,?,?)
                """, id, userId, clean(command.displayName(), 120), provider(command.providerType()), endpoint.toString(),
                clean(command.modelName(), 120), cipher.encrypt(apiKey), cipher.fingerprint(apiKey), lastFour(apiKey),
                Boolean.TRUE.equals(command.isDefault()), command.enabled() == null || command.enabled(),
                bounded(command.timeoutMs(), 5_000, 120_000, 60_000),
                bounded(command.maxOutputTokens(), 128, 32_768, 4096), temperature(command.temperature()));
        savePricing(id, userId, command.pricing());
        return owned(id, userId);
    }

    @Transactional
    public ConnectionView update(String id, ConnectionCommand command) {
        long userId = currentUser.id();
        if (ENVIRONMENT_ID.equals(id)) {
            if (!environmentConfigured()) throw new IllegalArgumentException("系统环境配置不存在");
            if (command == null || command.pricing() == null) throw new IllegalArgumentException("成本配置不能为空");
            saveEnvironmentPricing(userId, command.pricing());
            return environmentView(userId);
        }
        ConnectionRow existing = row(id, userId);
        validate(command, false);
        URI endpoint = endpointPolicy.validate(command.baseUrl());
        if (Boolean.TRUE.equals(command.isDefault())) clearDefault(userId);
        String encrypted = existing.encryptedApiKey();
        String fingerprint = existing.fingerprint();
        String lastFour = existing.lastFour();
        if (Boolean.TRUE.equals(command.clearApiKey())) { encrypted = null; fingerprint = null; lastFour = null; }
        else if (command.apiKey() != null && !command.apiKey().isBlank()) {
            if (command.apiKey().contains("****")) throw new IllegalArgumentException("不能把掩码作为 API Key 保存");
            encrypted = cipher.encrypt(command.apiKey()); fingerprint = cipher.fingerprint(command.apiKey()); lastFour = lastFour(command.apiKey());
        }
        int changed = jdbc.update("""
                UPDATE ai_model_connection SET display_name=?,provider_type=?,base_url=?,model_name=?,
                  encrypted_api_key=?,api_key_fingerprint=?,api_key_last_four=?,is_default=?,enabled=?,timeout_ms=?,
                  max_output_tokens=?,temperature=?,revision=revision+1
                WHERE id=? AND user_id=? AND revision=? AND deleted_at IS NULL
                """, clean(command.displayName(), 120), provider(command.providerType()), endpoint.toString(),
                clean(command.modelName(), 120), encrypted, fingerprint, lastFour, Boolean.TRUE.equals(command.isDefault()),
                command.enabled() == null || command.enabled(), bounded(command.timeoutMs(), 5_000, 120_000, 60_000),
                bounded(command.maxOutputTokens(), 128, 32_768, 4096), temperature(command.temperature()),
                id, userId, command.revision());
        if (changed != 1) throw new IllegalStateException("模型配置已变化，请刷新后重试");
        if (command.pricing() != null) savePricing(id, userId, command.pricing());
        return owned(id, userId);
    }

    @Transactional
    public void delete(String id) {
        if (ENVIRONMENT_ID.equals(id)) throw new IllegalArgumentException("系统环境配置不能删除");
        int changed = jdbc.update("UPDATE ai_model_connection SET enabled=FALSE,is_default=FALSE,deleted_at=CURRENT_TIMESTAMP(6),revision=revision+1 WHERE id=? AND user_id=? AND deleted_at IS NULL", id, currentUser.id());
        if (changed != 1) throw new IllegalArgumentException("模型配置不存在");
    }

    @Transactional
    public ConnectionView setDefault(String id) {
        if (ENVIRONMENT_ID.equals(id)) { clearDefault(currentUser.id()); return environmentView(currentUser.id()); }
        long userId = currentUser.id(); owned(id, userId); clearDefault(userId);
        jdbc.update("UPDATE ai_model_connection SET is_default=TRUE,enabled=TRUE,revision=revision+1 WHERE id=? AND user_id=?", id, userId);
        return owned(id, userId);
    }

    public TestResult test(String id) {
        RuntimeConnection connection = runtime(id, currentUser.id());
        long started = System.nanoTime();
        try {
            LlmGateway.AgentTurn result = gateway.agentTurnStreaming(connection.gatewayConfig(),
                    List.of(LlmGateway.AgentMessage.user("只回复 OK")), List.of(), ignored -> { });
            if (!ENVIRONMENT_ID.equals(id)) jdbc.update("UPDATE ai_model_connection SET connection_status='AVAILABLE',last_tested_at=CURRENT_TIMESTAMP(6),last_test_error_code=NULL WHERE id=? AND user_id=?", id, currentUser.id());
            return new TestResult(true, "AVAILABLE", connection.modelName(), elapsed(started), result.usage());
        } catch (Exception exception) {
            String code = classify(exception);
            if (!ENVIRONMENT_ID.equals(id)) jdbc.update("UPDATE ai_model_connection SET connection_status='FAILED',last_tested_at=CURRENT_TIMESTAMP(6),last_test_error_code=? WHERE id=? AND user_id=?", code, id, currentUser.id());
            return new TestResult(false, code, connection.modelName(), elapsed(started), LlmGateway.TokenUsage.empty());
        }
    }

    public RuntimeConnection resolveForSession(String sessionId, long userId) {
        List<String> ids = jdbc.query("SELECT model_connection_id FROM agent_session WHERE id=? AND user_id=?",
                (r, n) -> r.getString(1), sessionId, userId);
        if (ids.isEmpty()) throw new IllegalArgumentException("会话不存在");
        String id = ids.get(0);
        if (id != null) return runtime(id, userId);
        List<String> defaults = jdbc.query("SELECT id FROM ai_model_connection WHERE user_id=? AND is_default=TRUE AND enabled=TRUE AND deleted_at IS NULL LIMIT 1", (r, n) -> r.getString(1), userId);
        return defaults.isEmpty() ? runtime(ENVIRONMENT_ID, userId) : runtime(defaults.get(0), userId);
    }

    public RuntimeConnection resolveForTurn(String turnId, String sessionId, long userId) {
        List<TurnModelRow> retryModels = jdbc.query("""
                SELECT original.model_connection_id,original.provider_type
                FROM agent_turn retry
                JOIN agent_turn original ON original.id=retry.retry_of_turn_id AND original.user_id=retry.user_id
                WHERE retry.id=? AND retry.user_id=?
                """, (r, n) -> new TurnModelRow(r.getString(1), r.getString(2)), turnId, userId);
        if (!retryModels.isEmpty() && retryModels.get(0).providerType() != null) {
            String connectionId = retryModels.get(0).connectionId();
            return runtime(connectionId == null ? ENVIRONMENT_ID : connectionId, userId);
        }
        return resolveForSession(sessionId, userId);
    }

    public RuntimeConnection runtime(String id, long userId) {
        if (ENVIRONMENT_ID.equals(id)) {
            if (!environmentConfigured()) throw new IllegalStateException("系统环境模型未配置");
            return new RuntimeConnection(ENVIRONMENT_ID, "系统环境配置", "DEEPSEEK", environmentEndpoint,
                    URI.create(environmentEndpoint).getHost(), environmentModel, 0, 60_000, 4096,
                    new BigDecimal("0.1"), environmentKey, activeEnvironmentPricing(userId));
        }
        ConnectionRow row = row(id, userId);
        if (!row.enabled()) throw new IllegalStateException("模型配置已停用");
        // Resolve and validate again immediately before every outbound call.
        // This narrows the DNS-rebinding window after a configuration was saved.
        URI validatedEndpoint = endpointPolicy.validate(row.baseUrl());
        Pricing pricing = activePricing(id, userId);
        return new RuntimeConnection(row.id(), row.displayName(), row.providerType(), validatedEndpoint.toString(),
                validatedEndpoint.getHost(), row.modelName(), row.revision(), row.timeoutMs(),
                row.maxOutputTokens(), row.temperature(), cipher.decrypt(row.encryptedApiKey()), pricing);
    }

    public void bindSession(String sessionId, String connectionId) {
        long userId = currentUser.id();
        if (connectionId != null && !connectionId.isBlank() && !ENVIRONMENT_ID.equals(connectionId)) owned(connectionId, userId);
        jdbc.update("UPDATE agent_session SET model_connection_id=? WHERE id=? AND user_id=?", ENVIRONMENT_ID.equals(connectionId) ? null : connectionId, sessionId, userId);
    }

    private ConnectionView owned(String id, long userId) { return view(row(id, userId)); }
    private ConnectionRow row(String id, long userId) {
        return jdbc.query("SELECT * FROM ai_model_connection WHERE id=? AND user_id=? AND deleted_at IS NULL", (r,n)->row(r), id,userId).stream().findFirst().orElseThrow(() -> new IllegalArgumentException("模型配置不存在"));
    }
    private ConnectionView view(ResultSet r) throws SQLException { return view(row(r)); }
    private ConnectionView view(ConnectionRow r) {
        return new ConnectionView(r.id(), r.displayName(), r.providerType(), r.baseUrl(), r.modelName(),
                r.encryptedApiKey()!=null, r.lastFour()==null?null:"****"+r.lastFour(), r.isDefault(), r.enabled(),
                r.status(), r.lastTestedAt(), r.lastError(), r.timeoutMs(), r.maxOutputTokens(), r.temperature(),
                r.revision(), activePricing(r.id(), r.userId()), false);
    }
    private ConnectionRow row(ResultSet r) throws SQLException {
        var tested=r.getTimestamp("last_tested_at");
        return new ConnectionRow(r.getString("id"),r.getLong("user_id"),r.getString("display_name"),r.getString("provider_type"),r.getString("base_url"),r.getString("model_name"),r.getString("encrypted_api_key"),r.getString("api_key_fingerprint"),r.getString("api_key_last_four"),r.getBoolean("is_default"),r.getBoolean("enabled"),r.getString("connection_status"),tested==null?null:tested.toInstant(),r.getString("last_test_error_code"),r.getInt("timeout_ms"),r.getInt("max_output_tokens"),r.getBigDecimal("temperature"),r.getLong("revision"));
    }
    private ConnectionView environmentView(long userId) { return new ConnectionView(ENVIRONMENT_ID,"系统环境配置","DEEPSEEK",environmentEndpoint,environmentModel,true,mask(environmentKey),false,true,"AVAILABLE",null,null,60_000,4096,new BigDecimal("0.1"),0,activeEnvironmentPricing(userId),true); }
    private boolean environmentConfigured(){return environmentEndpoint!=null&&!environmentEndpoint.isBlank()&&environmentModel!=null&&!environmentModel.isBlank()&&environmentKey!=null&&!environmentKey.isBlank();}
    private void clearDefault(long userId){jdbc.update("UPDATE ai_model_connection SET is_default=FALSE WHERE user_id=? AND is_default=TRUE",userId);}
    private void savePricing(String connectionId,long userId,PricingCommand p){if(p==null)return;validatePrice(p);jdbc.update("UPDATE ai_model_pricing SET active=FALSE WHERE connection_id=? AND user_id=? AND active=TRUE",connectionId,userId);Integer v=jdbc.queryForObject("SELECT COALESCE(MAX(version),0)+1 FROM ai_model_pricing WHERE connection_id=?",Integer.class,connectionId);jdbc.update("INSERT INTO ai_model_pricing(connection_id,user_id,version,currency,pricing_mode,input_per_million,output_per_million,cache_hit_per_million,cache_miss_per_million,reasoning_per_million,off_peak_input_per_million,off_peak_output_per_million,off_peak_cache_hit_per_million,off_peak_cache_miss_per_million,off_peak_reasoning_per_million) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",connectionId,userId,v,currency(p),pricingMode(p),price(p.inputPerMillion()),price(p.outputPerMillion()),price(p.cacheHitPerMillion()),price(p.cacheMissPerMillion()),price(p.reasoningPerMillion()),price(p.offPeakInputPerMillion()),price(p.offPeakOutputPerMillion()),price(p.offPeakCacheHitPerMillion()),price(p.offPeakCacheMissPerMillion()),price(p.offPeakReasoningPerMillion()));}
    private void saveEnvironmentPricing(long userId,PricingCommand p){validatePrice(p);jdbc.update("UPDATE ai_environment_pricing SET active=FALSE WHERE user_id=? AND active=TRUE",userId);Integer v=jdbc.queryForObject("SELECT COALESCE(MAX(version),0)+1 FROM ai_environment_pricing WHERE user_id=?",Integer.class,userId);jdbc.update("INSERT INTO ai_environment_pricing(user_id,version,currency,pricing_mode,input_per_million,output_per_million,cache_hit_per_million,cache_miss_per_million,reasoning_per_million,off_peak_input_per_million,off_peak_output_per_million,off_peak_cache_hit_per_million,off_peak_cache_miss_per_million,off_peak_reasoning_per_million) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?)",userId,v,currency(p),pricingMode(p),price(p.inputPerMillion()),price(p.outputPerMillion()),price(p.cacheHitPerMillion()),price(p.cacheMissPerMillion()),price(p.reasoningPerMillion()),price(p.offPeakInputPerMillion()),price(p.offPeakOutputPerMillion()),price(p.offPeakCacheHitPerMillion()),price(p.offPeakCacheMissPerMillion()),price(p.offPeakReasoningPerMillion()));}
    private Pricing activePricing(String id,long userId){return jdbc.query("SELECT * FROM ai_model_pricing WHERE connection_id=? AND user_id=? AND active=TRUE ORDER BY version DESC LIMIT 1",(r,n)->pricing(r),id,userId).stream().findFirst().orElse(null);}
    private Pricing activeEnvironmentPricing(long userId){return jdbc.query("SELECT * FROM ai_environment_pricing WHERE user_id=? AND active=TRUE ORDER BY version DESC LIMIT 1",(r,n)->pricing(r),userId).stream().findFirst().orElse(null);}
    private Pricing pricing(ResultSet r)throws SQLException{return new Pricing(r.getInt("version"),r.getString("currency"),r.getString("pricing_mode"),r.getBigDecimal("input_per_million"),r.getBigDecimal("output_per_million"),r.getBigDecimal("cache_hit_per_million"),r.getBigDecimal("cache_miss_per_million"),r.getBigDecimal("reasoning_per_million"),r.getBigDecimal("off_peak_input_per_million"),r.getBigDecimal("off_peak_output_per_million"),r.getBigDecimal("off_peak_cache_hit_per_million"),r.getBigDecimal("off_peak_cache_miss_per_million"),r.getBigDecimal("off_peak_reasoning_per_million"));}
    private void validate(ConnectionCommand c,boolean creating){if(c==null)throw new IllegalArgumentException("配置不能为空"); clean(c.displayName(),120); clean(c.modelName(),120); provider(c.providerType()); if(creating&& (c.apiKey()==null||c.apiKey().isBlank()))throw new IllegalArgumentException("API Key 不能为空");}
    private void validatePrice(PricingCommand p){pricingMode(p);for(BigDecimal v:List.of(price(p.inputPerMillion()),price(p.outputPerMillion()),price(p.cacheHitPerMillion()),price(p.cacheMissPerMillion()),price(p.reasoningPerMillion()),price(p.offPeakInputPerMillion()),price(p.offPeakOutputPerMillion()),price(p.offPeakCacheHitPerMillion()),price(p.offPeakCacheMissPerMillion()),price(p.offPeakReasoningPerMillion())))if(v.signum()<0)throw new IllegalArgumentException("Token 单价不能为负数");}
    private String pricingMode(PricingCommand p){String value=p.pricingMode()==null?"FLAT":p.pricingMode().toUpperCase();if(!List.of("FLAT","DEEPSEEK_PEAK_OFFPEAK").contains(value))throw new IllegalArgumentException("不支持的计价模式");return value;}
    private String currency(PricingCommand p){return p.currency()==null||p.currency().isBlank()?"CNY":p.currency().toUpperCase();}
    private BigDecimal price(BigDecimal v){return v==null?BigDecimal.ZERO:v;}
    private String provider(String v){String p=required(v,"供应商不能为空").toUpperCase();if(!List.of("DEEPSEEK","OPENAI_COMPATIBLE").contains(p))throw new IllegalArgumentException("不支持的供应商");return p;}
    private String clean(String v,int max){String s=required(v,"字段不能为空").trim();if(s.length()>max)throw new IllegalArgumentException("字段过长");return s;}
    private String required(String v,String message){if(v==null||v.isBlank())throw new IllegalArgumentException(message);return v;}
    private int bounded(Integer v,int min,int max,int fallback){int n=v==null?fallback:v;if(n<min||n>max)throw new IllegalArgumentException("参数超出允许范围");return n;}
    private BigDecimal temperature(BigDecimal v){BigDecimal n=v==null?new BigDecimal("0.1"):v;if(n.signum()<0||n.compareTo(new BigDecimal("2"))>0)throw new IllegalArgumentException("temperature 必须在 0 到 2 之间");return n;}
    private String lastFour(String v){return v.substring(Math.max(0,v.length()-4));} private String mask(String v){return v==null?null:"****"+lastFour(v);} private long elapsed(long s){return Math.max(0,(System.nanoTime()-s)/1_000_000);} private String classify(Exception e){String m=String.valueOf(e.getMessage()).toLowerCase();if(m.contains("401")||m.contains("密钥")||m.contains("unauthorized"))return "AUTHENTICATION_FAILED";if(m.contains("timeout"))return "TIMEOUT";return "UPSTREAM_UNAVAILABLE";}

    public record ConnectionCommand(String displayName,String providerType,String baseUrl,String modelName,String apiKey,Boolean clearApiKey,Boolean isDefault,Boolean enabled,Integer timeoutMs,Integer maxOutputTokens,BigDecimal temperature,Long revision,PricingCommand pricing){}
    public record PricingCommand(String currency,String pricingMode,BigDecimal inputPerMillion,BigDecimal outputPerMillion,BigDecimal cacheHitPerMillion,BigDecimal cacheMissPerMillion,BigDecimal reasoningPerMillion,BigDecimal offPeakInputPerMillion,BigDecimal offPeakOutputPerMillion,BigDecimal offPeakCacheHitPerMillion,BigDecimal offPeakCacheMissPerMillion,BigDecimal offPeakReasoningPerMillion){}
    public record Pricing(int version,String currency,String pricingMode,BigDecimal inputPerMillion,BigDecimal outputPerMillion,BigDecimal cacheHitPerMillion,BigDecimal cacheMissPerMillion,BigDecimal reasoningPerMillion,BigDecimal offPeakInputPerMillion,BigDecimal offPeakOutputPerMillion,BigDecimal offPeakCacheHitPerMillion,BigDecimal offPeakCacheMissPerMillion,BigDecimal offPeakReasoningPerMillion){}
    public record ConnectionView(String id,String displayName,String providerType,String baseUrl,String modelName,boolean apiKeyConfigured,String apiKeyMask,boolean isDefault,boolean enabled,String connectionStatus,Instant lastTestedAt,String lastTestErrorCode,int timeoutMs,int maxOutputTokens,BigDecimal temperature,long revision,Pricing pricing,boolean systemManaged){}
    public record TestResult(boolean success,String status,String modelName,long durationMs,LlmGateway.TokenUsage usage){}
    public record RuntimeConnection(String id,String displayName,String providerType,String endpoint,String baseHost,String modelName,long revision,int timeoutMs,int maxOutputTokens,BigDecimal temperature,String apiKey,Pricing pricing){public LlmGateway.RuntimeConfig gatewayConfig(){return new LlmGateway.RuntimeConfig(endpoint,modelName,apiKey,temperature.doubleValue(),timeoutMs);}}
    private record ConnectionRow(String id,long userId,String displayName,String providerType,String baseUrl,String modelName,String encryptedApiKey,String fingerprint,String lastFour,boolean isDefault,boolean enabled,String status,Instant lastTestedAt,String lastError,int timeoutMs,int maxOutputTokens,BigDecimal temperature,long revision){}
    private record TurnModelRow(String connectionId, String providerType) { }
}
