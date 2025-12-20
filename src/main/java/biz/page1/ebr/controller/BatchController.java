package biz.page1.ebr.controller;

import biz.page1.ebr.domain.batch.Batch;
import biz.page1.ebr.domain.recipe.MasterRecipe;
import biz.page1.ebr.security.CustomUserDetails;
import biz.page1.ebr.service.BatchService;
import biz.page1.ebr.service.RecipeService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;

@Controller
@RequestMapping("/batches")
@RequiredArgsConstructor
public class BatchController {

    private final BatchService batchService;
    private final RecipeService recipeService;

    @GetMapping
    public String list(Model model) {
        model.addAttribute("batches", batchService.findAll());
        return "batch/list";
    }

    @GetMapping("/new")
    public String newForm(Model model) {
        List<MasterRecipe> recipes = recipeService.findApprovedRecipes();
        model.addAttribute("recipes", recipes);
        return "batch/form";
    }

    @PostMapping
    public String create(@RequestParam Long recipeId,
                         @RequestParam(required = false) Double plannedQuantity,
                         @RequestParam(required = false) String quantityUnit,
                         @AuthenticationPrincipal CustomUserDetails userDetails,
                         RedirectAttributes redirectAttributes) {
        try {
            Batch batch = batchService.createBatch(recipeId, plannedQuantity, quantityUnit, userDetails.getUser());
            redirectAttributes.addFlashAttribute("successMessage", "Batch created: " + batch.getBatchNumber());
            return "redirect:/batches/" + batch.getId();
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
            return "redirect:/batches/new";
        }
    }

    @GetMapping("/{id}")
    public String detail(@PathVariable Long id, Model model) {
        Batch batch = batchService.findByIdWithSteps(id);
        model.addAttribute("batch", batch);
        return "batch/detail";
    }

    @PostMapping("/{id}/release")
    public String release(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        try {
            batchService.releaseBatch(id);
            redirectAttributes.addFlashAttribute("successMessage", "Batch released successfully");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/batches/" + id;
    }

    @PostMapping("/{id}/cancel")
    public String cancel(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        try {
            batchService.cancelBatch(id);
            redirectAttributes.addFlashAttribute("successMessage", "Batch cancelled");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/batches/" + id;
    }
}
