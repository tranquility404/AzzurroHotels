package com.tranquility.web;

import com.tranquility.dashboard.exception.InvalidReviewCursorException;
import com.tranquility.dashboard.exception.InvalidDashboardFilterException;
import com.tranquility.dashboard.exception.HotelReviewSearchException;
import com.tranquility.dashboard.exception.HotelSyncConflictException;
import com.tranquility.dashboard.exception.HotelSyncNotFoundException;
import com.tranquility.dashboard.exception.HotelSyncProviderException;
import com.tranquility.dashboard.exception.HotelSyncRateLimitException;
import com.tranquility.insights.exception.InsightsAgentUnavailableException;
import com.tranquility.insights.exception.LlmCallQuotaExceededException;
import com.tranquility.insights.exception.LlmPromptTooLargeException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(InvalidDashboardFilterException.class)
    public ResponseEntity<ProblemDetail> handleInvalidDashboardFilter(InvalidDashboardFilterException exception) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, exception.getMessage());
        problem.setTitle("Invalid dashboard filter");
        return ResponseEntity.badRequest().body(problem);
    }

    @ExceptionHandler(InvalidReviewCursorException.class)
    public ResponseEntity<ProblemDetail> handleInvalidReviewCursor(InvalidReviewCursorException exception) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, exception.getMessage());
        problem.setTitle("Invalid review cursor");
        return ResponseEntity.badRequest().body(problem);
    }

    @ExceptionHandler(HotelReviewSearchException.class)
    public ResponseEntity<ProblemDetail> handleInvalidHotelReviewSearch(HotelReviewSearchException exception) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, exception.getMessage());
        problem.setTitle("Invalid hotel review search");
        return ResponseEntity.badRequest().body(problem);
    }

    @ExceptionHandler(InsightsAgentUnavailableException.class)
    public ResponseEntity<ProblemDetail> handleInsightsAgentUnavailable(InsightsAgentUnavailableException exception) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE, exception.getMessage());
        problem.setTitle("Guest insights assistant unavailable");
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(problem);
    }

    @ExceptionHandler(LlmCallQuotaExceededException.class)
    public ResponseEntity<ProblemDetail> handleInsightsLlmQuota(LlmCallQuotaExceededException exception) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.TOO_MANY_REQUESTS, exception.getMessage());
        problem.setTitle("Guest insights LLM call limit reached");
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(problem);
    }

    @ExceptionHandler(LlmPromptTooLargeException.class)
    public ResponseEntity<ProblemDetail> handleInsightsPromptTooLarge(LlmPromptTooLargeException exception) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.PAYLOAD_TOO_LARGE, exception.getMessage());
        problem.setTitle("Guest insights prompt exceeds the token limit");
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE).body(problem);
    }

    @ExceptionHandler(HotelSyncNotFoundException.class)
    public ResponseEntity<ProblemDetail> handleHotelSyncNotFound(HotelSyncNotFoundException exception) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, exception.getMessage());
        problem.setTitle("Hotel not found");
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(problem);
    }

    @ExceptionHandler(HotelSyncRateLimitException.class)
    public ResponseEntity<ProblemDetail> handleHotelSyncRateLimit(HotelSyncRateLimitException exception) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.TOO_MANY_REQUESTS, exception.getMessage());
        problem.setTitle("Hotel sync rate limit reached");
        problem.setProperty("nextAllowedAt", exception.getNextAllowedAt().toString());
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(problem);
    }

    @ExceptionHandler(HotelSyncConflictException.class)
    public ResponseEntity<ProblemDetail> handleHotelSyncConflict(HotelSyncConflictException exception) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, exception.getMessage());
        problem.setTitle("Hotel sync cannot continue");
        return ResponseEntity.status(HttpStatus.CONFLICT).body(problem);
    }

    @ExceptionHandler(HotelSyncProviderException.class)
    public ResponseEntity<ProblemDetail> handleHotelSyncProviderFailure(HotelSyncProviderException exception) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_GATEWAY, exception.getMessage());
        problem.setTitle("Hotel sync failed");
        if (exception.getNextAllowedAt() != null) {
            problem.setProperty("nextAllowedAt", exception.getNextAllowedAt().toString());
        }
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(problem);
    }
}
