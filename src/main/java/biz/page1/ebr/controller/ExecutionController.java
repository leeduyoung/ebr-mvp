package biz.page1.ebr.controller;

import biz.page1.ebr.domain.batch.Batch;
import biz.page1.ebr.domain.batch.BatchStep;
import biz.page1.ebr.security.CustomUserDetails;
import biz.page1.ebr.service.ExecutionService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.HashMap;
import java.util.Map;

@Controller
@RequestMapping("/execution")
@RequiredArgsConstructor
public class ExecutionController {

    private final ExecutionService executionService;

    @GetMapping
    public String listExecutableBatches(Model model) {
        model.addAttribute("batches", executionService.findExecutableBatches());
        return "execution/batch-list";
    }

    @GetMapping("/{batchId}")
    public String batchExecution(@PathVariable Long batchId,
                                  @AuthenticationPrincipal CustomUserDetails userDetails,
                                  Model model) {
        Batch batch = executionService.findBatchWithSteps(batchId);
        model.addAttribute("batch", batch);
        model.addAttribute("currentUser", userDetails.getUser());

        // Find next executable step
        BatchStep nextStep = batch.getCurrentStep();
        if (nextStep == null) {
            nextStep = batch.getNextPendingStep();
        }
        model.addAttribute("nextStep", nextStep);

        // Check if user can execute next step
        if (nextStep != null && nextStep.isPending()) {
            var validation = executionService.canStartStep(batch, nextStep.getStepNumber(), userDetails.getUser());
            model.addAttribute("canStartNextStep", validation.isValid());
            model.addAttribute("validationErrors", validation.getErrors());
        }

        return "execution/batch-execution";
    }

    @GetMapping("/{batchId}/steps/{stepNumber}")
    public String stepExecution(@PathVariable Long batchId,
                                @PathVariable int stepNumber,
                                @AuthenticationPrincipal CustomUserDetails userDetails,
                                Model model) {
        Batch batch = executionService.findBatchWithSteps(batchId);
        BatchStep step = executionService.findStepWithDetails(batchId, stepNumber);

        model.addAttribute("batch", batch);
        model.addAttribute("step", step);
        model.addAttribute("currentUser", userDetails.getUser());

        // Check validation for pending step
        if (step.isPending()) {
            var validation = executionService.canStartStep(batch, stepNumber, userDetails.getUser());
            model.addAttribute("canStart", validation.isValid());
            model.addAttribute("validationErrors", validation.getErrors());
        }

        return "execution/step-execution";
    }

    @PostMapping("/{batchId}/steps/{stepNumber}/start")
    public String startStep(@PathVariable Long batchId,
                            @PathVariable int stepNumber,
                            @AuthenticationPrincipal CustomUserDetails userDetails,
                            RedirectAttributes redirectAttributes) {
        try {
            executionService.startStep(batchId, stepNumber, userDetails.getUser());
            redirectAttributes.addFlashAttribute("successMessage", "Step started");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/execution/" + batchId + "/steps/" + stepNumber;
    }

    @PostMapping("/{batchId}/steps/{stepNumber}/complete")
    public String completeStep(@PathVariable Long batchId,
                               @PathVariable int stepNumber,
                               @RequestParam Map<String, String> allParams,
                               @AuthenticationPrincipal CustomUserDetails userDetails,
                               RedirectAttributes redirectAttributes) {
        try {
            // Extract parameter values (param_{id} -> value)
            Map<Long, String> parameterValues = new HashMap<>();
            for (Map.Entry<String, String> entry : allParams.entrySet()) {
                if (entry.getKey().startsWith("param_")) {
                    Long paramId = Long.parseLong(entry.getKey().substring(6));
                    parameterValues.put(paramId, entry.getValue());
                }
            }

            executionService.completeStep(batchId, stepNumber, parameterValues, userDetails.getUser());
            redirectAttributes.addFlashAttribute("successMessage", "Step completed successfully");
            return "redirect:/execution/" + batchId;
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
            return "redirect:/execution/" + batchId + "/steps/" + stepNumber;
        }
    }
}
