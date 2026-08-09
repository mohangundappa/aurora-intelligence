package com.aurora.signals;

import org.springframework.stereotype.Component;

@Component
public class BusinessTravelAffinityCalculator extends TextAffinityCalculator {
  public BusinessTravelAffinityCalculator() {
    super(
        "business-travel-affinity",
        "business",
        "Business-oriented inventory was explored.",
        "Limited business-travel evidence.");
  }
}
