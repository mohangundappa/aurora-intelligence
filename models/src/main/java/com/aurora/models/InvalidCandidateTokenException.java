package com.aurora.models;

final class InvalidCandidateTokenException extends RuntimeException {
  InvalidCandidateTokenException(String message) {
    super(message);
  }
}
