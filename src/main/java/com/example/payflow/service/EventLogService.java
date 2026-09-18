package com.example.payflow.service;

import com.example.payflow.entity.TransactionEvent;
import com.example.payflow.exception.InvalidRequestException;
import com.example.payflow.exception.TransactionNotFoundException;
import com.example.payflow.repository.TransactionEventRepository;
import com.example.payflow.repository.TransactionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Read side of the transfer event log. Each transfer is a case, each life-cycle step an event,
 * which is the input format process-mining tools expect (case id, activity, timestamp).
 */
@Service
public class EventLogService {
    private static final LocalDateTime EARLIEST = LocalDateTime.of(1970, 1, 1, 0, 0);
    private static final LocalDateTime LATEST = LocalDateTime.of(9999, 12, 31, 23, 59);

    private final TransactionRepository transactionRepository;
    private final TransactionEventRepository eventRepository;

    public EventLogService(TransactionRepository transactionRepository, TransactionEventRepository eventRepository) {
        this.transactionRepository = transactionRepository;
        this.eventRepository = eventRepository;
    }

    @Transactional(readOnly = true)
    public List<TransactionEvent> eventsFor(Long transactionId) {
        if (!transactionRepository.existsById(transactionId)) {
            throw new TransactionNotFoundException(transactionId);
        }
        return eventRepository.findByTransactionIdOrderByOccurredAtAscEventIdAsc(transactionId);
    }

    @Transactional(readOnly = true)
    public List<TransactionEvent> eventsBetween(LocalDateTime from, LocalDateTime to) {
        LocalDateTime start = from == null ? EARLIEST : from;
        LocalDateTime end = to == null ? LATEST : to;
        if (start.isAfter(end)) {
            throw new InvalidRequestException("'from' must not be after 'to'");
        }
        return eventRepository.findByOccurredAtBetweenOrderByTransactionIdAscOccurredAtAscEventIdAsc(start, end);
    }

    // CSV with one row per event, ordered by case then time, ready to load into a process-mining tool
    public static String toCsv(List<TransactionEvent> events) {
        StringBuilder csv = new StringBuilder("case_id,activity,timestamp,details\n");
        for (TransactionEvent event : events) {
            csv.append(event.getTransactionId()).append(',')
                    .append(event.getActivity()).append(',')
                    .append(event.getOccurredAt()).append(',')
                    .append(csvField(event.getDetails())).append('\n');
        }
        return csv.toString();
    }

    private static String csvField(String value) {
        if (value == null) {
            return "";
        }
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }
}
