package com.workbench.workshop.infrastructure.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sns.model.PublishRequest;
import software.amazon.awssdk.services.sns.model.PublishResponse;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ExecutionEventPublisherTest {

    private SnsClient snsClient;
    private ExecutionEventPublisher publisher;

    @BeforeEach
    void setUp() {
        snsClient = mock(SnsClient.class);
        MessagingProperties props = new MessagingProperties();
        props.setExecutionEventsTopicArn("arn:aws:sns:us-east-1:000000000000:workbench-execution-events");
        publisher = new ExecutionEventPublisher(snsClient, props, new ObjectMapper());
    }

    @Test
    void publishesEnvelopeToTopic() {
        when(snsClient.publish(any(PublishRequest.class))).thenReturn(PublishResponse.builder().messageId("m1").build());

        publisher.publish(MessageEnvelope.of("ReservaConfirmada", "saga-1", "os-1", Map.of("reservations", 2)));

        ArgumentCaptor<PublishRequest> captor = ArgumentCaptor.forClass(PublishRequest.class);
        verify(snsClient).publish(captor.capture());
        assertTrue(captor.getValue().topicArn().endsWith("workbench-execution-events"));
        assertTrue(captor.getValue().message().contains("ReservaConfirmada"));
    }

    @Test
    void throwsWhenSnsFails() {
        when(snsClient.publish(any(PublishRequest.class))).thenThrow(new RuntimeException("sns down"));

        assertThrows(IllegalStateException.class,
                () -> publisher.publish(MessageEnvelope.of("ReservaFalhou", "saga-1", "os-1", Map.of())));
    }
}
