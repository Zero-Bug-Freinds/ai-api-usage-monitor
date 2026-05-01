package com.eevee.billingservice.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class BillingRabbitConfiguration {

    @Bean
    public TopicExchange billingUsageExchange(BillingRabbitProperties props) {
        return new TopicExchange(props.getExchange(), true, false);
    }

    /**
     * Outbound exchange for {@link com.eevee.usage.events.UsageCostFinalizedEvent} (separate from inbound {@code usage.events}).
     */
    @Bean
    public TopicExchange billingCostEventsExchange(BillingRabbitProperties props) {
        return new TopicExchange(props.getCostOut().getExchange(), true, false);
    }

    @Bean
    public TopicExchange billingBudgetEventsExchange(BillingRabbitProperties props) {
        return new TopicExchange(props.getBudgetOut().getExchange(), true, false);
    }

    /**
     * Topic exchange for inbound cost corrections (defaults to {@code billing.events}; may differ per env).
     */
    @Bean
    public TopicExchange billingCorrectionIngressExchange(BillingRabbitProperties props) {
        return new TopicExchange(props.getCorrectionIn().getExchange(), true, false);
    }

    @Bean
    public Queue billingQueue(BillingRabbitProperties props) {
        return new Queue(props.getQueue(), true);
    }

    @Bean
    public Binding billingBinding(
            Queue billingQueue,
            TopicExchange billingUsageExchange,
            BillingRabbitProperties props
    ) {
        return BindingBuilder.bind(billingQueue)
                .to(billingUsageExchange)
                .with(props.getRoutingKey());
    }

    @Bean
    public Queue billingCostCorrectionQueue(BillingRabbitProperties props) {
        return new Queue(props.getCorrectionIn().getQueue(), true);
    }

    @Bean
    public Binding billingCostCorrectionBinding(
            Queue billingCostCorrectionQueue,
            TopicExchange billingCorrectionIngressExchange,
            BillingRabbitProperties props
    ) {
        return BindingBuilder.bind(billingCostCorrectionQueue)
                .to(billingCorrectionIngressExchange)
                .with(props.getCorrectionIn().getRoutingKey());
    }
}
