package net.hytaledepot.templates.plugin.core;

import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

public final class CoreDemoService {
  private final Map<String, AtomicLong> actionCounters = new ConcurrentHashMap<>();
  private final Map<String, String> lastActionBySender = new ConcurrentHashMap<>();
  private final Map<String, String> moduleHealth = new ConcurrentHashMap<>();
  private volatile Path dataDirectory;

  public void initialize(Path dataDirectory) {
    this.dataDirectory = dataDirectory;
    moduleHealth.clear();
    moduleHealth.put("commands", "healthy");
    moduleHealth.put("heartbeat", "healthy");
    moduleHealth.put("storage", "healthy");
    moduleHealth.put("integrations", "healthy");
  }

  public void onHeartbeat(long tick) {
    actionCounters.computeIfAbsent("heartbeat", key -> new AtomicLong()).incrementAndGet();
    if (tick % 120 == 0) {
      moduleHealth.putIfAbsent("heartbeat", "healthy");
    }
  }

  public void recordExternalEvent(String key) {
    actionCounters.computeIfAbsent(String.valueOf(key), item -> new AtomicLong()).incrementAndGet();
  }

  public String applyAction(CorePluginState state, String sender, String action, long heartbeatTicks) {
    String normalizedSender = String.valueOf(sender == null ? "unknown" : sender);
    String normalizedAction = normalizeAction(action);

    actionCounters.computeIfAbsent(normalizedAction, key -> new AtomicLong()).incrementAndGet();
    lastActionBySender.put(normalizedSender, normalizedAction);

    if ("toggle".equals(normalizedAction)) {
      boolean enabled = state.toggleDemoFlag();
      return "[Core] demoFlag=" + enabled + ", heartbeatTicks=" + heartbeatTicks;
    }

    if ("info".equals(normalizedAction)) {
      return "[Core] " + diagnostics();
    }

    String domainResult = handleDomainAction(normalizedSender, normalizedAction, heartbeatTicks);
    if (domainResult != null) {
      return "[Core] " + domainResult;
    }

    return "[Core] unknown action='" + normalizedAction + "' (try: info, toggle, sample, module-scan, mark-unhealthy, mark-healthy)";
  }

  public String describeLastAction(String sender) {
    return lastActionBySender.getOrDefault(String.valueOf(sender), "none");
  }

  public long operationCount() {
    long total = 0;
    for (AtomicLong value : actionCounters.values()) {
      total += value.get();
    }
    return total;
  }

  public String diagnostics() {
    String directory = dataDirectory == null ? "unset" : dataDirectory.toString();
    long healthy = moduleHealth.values().stream().filter("healthy"::equals).count();
    return "ops=" + operationCount()
        + ", modules=" + moduleHealth.size()
        + ", healthy=" + healthy
        + ", degraded=" + (moduleHealth.size() - healthy)
        + ", dataDirectory=" + directory;
  }

  public void shutdown() {
    moduleHealth.clear();
  }

  private String handleDomainAction(String sender, String action, long heartbeatTicks) {
    if ("sample".equals(action) || "module-scan".equals(action)) {
      long healthy = moduleHealth.values().stream().filter("healthy"::equals).count();
      return "module scan complete, healthy=" + healthy + "/" + moduleHealth.size();
    }
    if ("mark-unhealthy".equals(action)) {
      moduleHealth.put("integrations", "degraded");
      return "integrations marked degraded";
    }
    if ("mark-healthy".equals(action)) {
      moduleHealth.replaceAll((key, value) -> "healthy");
      return "all modules marked healthy";
    }
    return null;
  }

  private static String normalizeAction(String action) {
    String normalized = String.valueOf(action == null ? "" : action).trim().toLowerCase();
    return normalized.isEmpty() ? "sample" : normalized;
  }
}
