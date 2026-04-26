package com.eevee.usageservice.config;

import org.aopalliance.aop.Advice;
import org.springframework.amqp.core.AcknowledgeMode;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.config.RetryInterceptorBuilder;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.retry.RepublishMessageRecoverer;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Map;

@Configuration
@ConditionalOnProperty(prefix = "usage.rabbit.summary-aggregation", name = "enabled", havingValue = "true", matchIfMissing = true)
public class UsageSummaryAggregationRabbitConfiguration {

    @Bean
    public TopicExchange usageSummaryAggregationExchange(UsageRabbitProperties props) {
        return new TopicExchange(props.getSummaryAggregation().getExchange(), true, false);
    }

    @Bean
    public TopicExchange usageSummaryAggregationDlx(UsageRabbitProperties props) {
        return new TopicExchange(props.getSummaryAggregation().getDlx(), true, false);
    }

    @Bean
    public Queue usageSummaryAggregationQueue(UsageRabbitProperties props) {
        return new Queue(
                props.getSummaryAggregation().getQueue(),
                true,
                false,
                false,
                Map.of(
                        "x-dead-letter-exchange", props.getSummaryAggregation().getDlx(),
                        "x-dead-letter-routing-key", props.getSummaryAggregation().getDlq()
                )
        );
    }

    @Bean
    public Queue usageSummaryAggregationDlq(UsageRabbitProperties props) {
        return new Queue(props.getSummaryAggregation().getDlq(), true);
    }

    @Bean
    public Binding usageSummaryAggregationBinding(
            @Qualifier("usageSummaryAggregationQueue") Queue queue,
            @Qualifier("usageSummaryAggregationExchange") TopicExchange exchange,
            UsageRabbitProperties props
    ) {
        return BindingBuilder.bind(queue)
                .to(exchange)
                .with(props.getSummaryAggregation().getRoutingKey());
    }

    @Bean
    public Binding usageSummaryAggregationDlqBinding(
            @Qualifier("usageSummaryAggregationDlq") Queue dlq,
            @Qualifier("usageSummaryAggregationDlx") TopicExchange dlx,
            UsageRabbitProperties props
    ) {
        return BindingBuilder.bind(dlq)
                .to(dlx)
                .with(props.getSummaryAggregation().getDlq());
    }

    @Bean
    public Advice usageSummaryAggregationRetryInterceptor(
            RabbitTemplate rabbitTemplate,
            UsageRabbitProperties props
    ) {
        RepublishMessageRecoverer recoverer = new RepublishMessageRecoverer(
                rabbitTemplate,
                props.getSummaryAggregation().getDlx(),
                props.getSummaryAggregation().getDlq()
        );
        return RetryInterceptorBuilder.stateless()
                .recoverer(recoverer)
                .build();
    }

    @Bean
    public SimpleRabbitListenerContainerFactory summaryAggregationRabbitListenerContainerFactory(
            ConnectionFactory connectionFactory,
            @Qualifier("usageSummaryAggregationRetryInterceptor") Advice retryInterceptor
    ) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setAcknowledgeMode(AcknowledgeMode.AUTO);
        factory.setConcurrentConsumers(2);
        factory.setMaxConcurrentConsumers(6);
        factory.setPrefetchCount(50);
        factory.setAdviceChain(retryInterceptor);
        factory.setDefaultRequeueRejected(false);
        return factory;
    }
}
