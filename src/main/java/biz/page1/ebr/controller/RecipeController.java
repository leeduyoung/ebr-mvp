package biz.page1.ebr.controller;

import biz.page1.ebr.domain.recipe.MasterRecipe;
import biz.page1.ebr.domain.recipe.ParameterType;
import biz.page1.ebr.domain.user.Qualification;
import biz.page1.ebr.repository.QualificationRepository;
import biz.page1.ebr.security.CustomUserDetails;
import biz.page1.ebr.service.RecipeService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;

@Controller
@RequestMapping("/recipes")
@RequiredArgsConstructor
public class RecipeController {

    private final RecipeService recipeService;
    private final QualificationRepository qualificationRepository;

    @GetMapping
    public String list(Model model) {
        model.addAttribute("recipes", recipeService.findAll());
        return "recipe/list";
    }

    @GetMapping("/new")
    public String newForm(Model model) {
        return "recipe/form";
    }

    @PostMapping
    public String create(@RequestParam String code,
                         @RequestParam String name,
                         @RequestParam(required = false) String description,
                         @AuthenticationPrincipal CustomUserDetails userDetails,
                         RedirectAttributes redirectAttributes) {
        try {
            MasterRecipe recipe = recipeService.createRecipe(code, name, description, userDetails.getUser());
            redirectAttributes.addFlashAttribute("successMessage", "Recipe created successfully");
            return "redirect:/recipes/" + recipe.getId();
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
            return "redirect:/recipes/new";
        }
    }

    @GetMapping("/{id}")
    public String detail(@PathVariable Long id, Model model) {
        MasterRecipe recipe = recipeService.findByIdWithSteps(id);
        List<Qualification> qualifications = qualificationRepository.findAll();

        model.addAttribute("recipe", recipe);
        model.addAttribute("qualifications", qualifications);
        model.addAttribute("parameterTypes", ParameterType.values());
        return "recipe/detail";
    }

    @GetMapping("/{id}/edit")
    public String editForm(@PathVariable Long id, Model model) {
        model.addAttribute("recipe", recipeService.findById(id));
        return "recipe/form";
    }

    @PostMapping("/{id}")
    public String update(@PathVariable Long id,
                         @RequestParam String name,
                         @RequestParam(required = false) String description,
                         RedirectAttributes redirectAttributes) {
        try {
            recipeService.updateRecipe(id, name, description);
            redirectAttributes.addFlashAttribute("successMessage", "Recipe updated successfully");
        } catch (IllegalStateException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/recipes/" + id;
    }

    @PostMapping("/{id}/approve")
    public String approve(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        try {
            recipeService.approveRecipe(id);
            redirectAttributes.addFlashAttribute("successMessage", "Recipe approved successfully");
        } catch (IllegalStateException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/recipes/" + id;
    }

    @PostMapping("/{id}/steps")
    public String addStep(@PathVariable Long id,
                          @RequestParam String name,
                          @RequestParam(required = false) String description,
                          @RequestParam(required = false) List<Long> qualificationIds,
                          RedirectAttributes redirectAttributes) {
        try {
            recipeService.addStep(id, name, description, qualificationIds);
            redirectAttributes.addFlashAttribute("successMessage", "Step added successfully");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/recipes/" + id;
    }

    @PostMapping("/{recipeId}/steps/{stepId}/parameters")
    public String addParameter(@PathVariable Long recipeId,
                               @PathVariable Long stepId,
                               @RequestParam String name,
                               @RequestParam ParameterType parameterType,
                               @RequestParam(defaultValue = "true") boolean required,
                               @RequestParam(required = false) Double minValue,
                               @RequestParam(required = false) Double maxValue,
                               @RequestParam(required = false) String unit,
                               RedirectAttributes redirectAttributes) {
        try {
            recipeService.addParameter(stepId, name, parameterType, required, minValue, maxValue, unit);
            redirectAttributes.addFlashAttribute("successMessage", "Parameter added successfully");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/recipes/" + recipeId;
    }

    @PostMapping("/{recipeId}/steps/{stepId}/delete")
    public String deleteStep(@PathVariable Long recipeId,
                             @PathVariable Long stepId,
                             RedirectAttributes redirectAttributes) {
        try {
            recipeService.deleteStep(recipeId, stepId);
            redirectAttributes.addFlashAttribute("successMessage", "Step deleted successfully");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/recipes/" + recipeId;
    }
}
