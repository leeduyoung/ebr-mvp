package biz.page1.ebr.controller;

import biz.page1.ebr.domain.batch.BatchStatus;
import biz.page1.ebr.security.CustomUserDetails;
import biz.page1.ebr.service.BatchService;
import biz.page1.ebr.service.RecipeService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
@RequiredArgsConstructor
public class HomeController {

    private final BatchService batchService;
    private final RecipeService recipeService;

    @GetMapping("/")
    public String dashboard(@AuthenticationPrincipal CustomUserDetails userDetails, Model model) {

        System.out.println("Called /");

        model.addAttribute("user", userDetails.getUser());

        // Batch statistics
        model.addAttribute("createdCount", batchService.countByStatus(BatchStatus.CREATED));
        model.addAttribute("releasedCount", batchService.countByStatus(BatchStatus.RELEASED));
        model.addAttribute("inProgressCount", batchService.countByStatus(BatchStatus.IN_PROGRESS));
        model.addAttribute("completedCount", batchService.countByStatus(BatchStatus.COMPLETED));

        // Recent batches
        model.addAttribute("recentBatches", batchService.findAll().stream().limit(5).toList());

        // Executable batches for operators
        model.addAttribute("executableBatches", batchService.findExecutableBatches());

        // Approved recipes count
        model.addAttribute("approvedRecipes", recipeService.findApprovedRecipes().size());

        return "home/dashboard";
    }
}
