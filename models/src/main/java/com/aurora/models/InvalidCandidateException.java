package com.aurora.models;

final class InvalidCandidateException extends IllegalArgumentException {
  InvalidCandidateException(String message) {
    super(message);
  }
}
