package org.example.mcp.tools;

import org.example.automation.AutomationEventRepository;
import org.example.automation.RuleRepository;
import org.example.mcp.ViewMapper;
import org.example.mcp.dto.AutomationEventView;
import org.example.mcp.dto.RuleView;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class AutomationTools {

    private final RuleRepository ruleRepository;
    private final AutomationEventRepository eventRepository;
    private final ViewMapper viewMapper;

    public AutomationTools(RuleRepository ruleRepository,
                           AutomationEventRepository eventRepository,
                           ViewMapper viewMapper) {
        this.ruleRepository  = ruleRepository;
        this.eventRepository = eventRepository;
        this.viewMapper      = viewMapper;
    }

    @Tool(name = "listRules",
          description = "List all automation rules with their trigger time, days, target device and the JSON action payload. " +
                        "Includes both enabled and disabled rules.")
    public List<RuleView> listRules() {
        return ruleRepository.findAll().stream()
                .map(viewMapper::toRuleView)
                .toList();
    }

    @Tool(name = "recentAutomationEvents",
          description = "Fetch the most-recent automation rule firings (descending by time). " +
                        "Use this to diagnose why a device changed state at a given moment.")
    public List<AutomationEventView> recentAutomationEvents(
            @ToolParam(required = false,
                       description = "Maximum number of events to return. Defaults to 50 if null. Capped at 500.")
            Integer limit) {
        int n = (limit == null || limit < 1) ? 50 : Math.min(limit, 500);
        return eventRepository.findAllByOrderByFiredAtDesc(PageRequest.of(0, n)).stream()
                .map(viewMapper::toEventView)
                .toList();
    }

    @Tool(name = "setRuleEnabled",
          description = "Enable or disable an automation rule without modifying its configuration. " +
                        "Returns the updated rule view, or null if no rule with that id exists.")
    public RuleView setRuleEnabled(
            @ToolParam(description = "Rule id from listRules.")
            long ruleId,
            @ToolParam(description = "true to enable the rule, false to disable it.")
            boolean enabled) {
        return ruleRepository.findById(ruleId).map(rule -> {
            rule.setEnabled(enabled);
            return viewMapper.toRuleView(ruleRepository.save(rule));
        }).orElse(null);
    }
}
