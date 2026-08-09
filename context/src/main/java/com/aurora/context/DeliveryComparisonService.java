package com.aurora.context;

import java.io.InputStream;
import java.util.List;
import java.util.Map;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Service;
import org.yaml.snakeyaml.Yaml;

@Service
public class DeliveryComparisonService {
  private final List<Assumption> assumptions;

  @SuppressWarnings("unchecked")
  public DeliveryComparisonService(ResourceLoader loader) {
    try {
      Resource resource = loader.getResource("classpath:delivery-assumptions.yaml");
      try (InputStream input = resource.getInputStream()) {
        Map<String, Object> root = new Yaml().load(input);
        assumptions =
            ((List<Map<String, Object>>) root.get("activities"))
                .stream()
                    .map(
                        item ->
                            new Assumption(
                                String.valueOf(item.get("activity")),
                                ((Number) item.get("traditionalDays")).intValue(),
                                ((Number) item.get("acceleratedDays")).intValue(),
                                String.valueOf(item.get("rationale"))))
                    .toList();
      }
    } catch (Exception exception) {
      throw new IllegalStateException("Unable to load delivery assumptions", exception);
    }
  }

  public DeliveryComparison comparison() {
    int traditional = assumptions.stream().mapToInt(Assumption::traditionalDays).sum();
    int accelerated = assumptions.stream().mapToInt(Assumption::acceleratedDays).sum();
    double reduction = traditional == 0 ? 0 : (double) (traditional - accelerated) / traditional;
    return new DeliveryComparison(assumptions, traditional, accelerated, reduction);
  }

  public record Assumption(
      String activity, int traditionalDays, int acceleratedDays, String rationale) {}

  public record DeliveryComparison(
      List<Assumption> assumptions,
      int traditionalDays,
      int acceleratedDays,
      double reduction,
      String label) {
    public DeliveryComparison(
        List<Assumption> assumptions, int traditionalDays, int acceleratedDays, double reduction) {
      this(
          assumptions,
          traditionalDays,
          acceleratedDays,
          reduction,
          "Assumption-derived target, not a measured commercial result.");
    }
  }
}
