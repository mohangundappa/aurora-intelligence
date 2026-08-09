package com.aurora.signals;

import org.springframework.stereotype.Component;

@Component
public class ResortAffinityCalculator extends TextAffinityCalculator {
  public ResortAffinityCalculator() {
    super(
        "resort-affinity",
        "resort",
        "Resort inventory or filtering was explored.",
        "Limited resort preference evidence.");
  }
}
