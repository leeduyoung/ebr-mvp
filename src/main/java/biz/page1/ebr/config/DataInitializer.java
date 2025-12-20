package biz.page1.ebr.config;

import biz.page1.ebr.domain.recipe.*;
import biz.page1.ebr.domain.user.Qualification;
import biz.page1.ebr.domain.user.User;
import biz.page1.ebr.domain.user.UserRole;
import biz.page1.ebr.repository.MasterRecipeRepository;
import biz.page1.ebr.repository.QualificationRepository;
import biz.page1.ebr.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@RequiredArgsConstructor
public class DataInitializer implements CommandLineRunner {

    private final UserRepository userRepository;
    private final QualificationRepository qualificationRepository;
    private final MasterRecipeRepository recipeRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    @Transactional
    public void run(String... args) {
        if (userRepository.count() > 0) {
            log.info("Data already initialized, skipping...");
            return;
        }

        log.info("Initializing demo data...");

        // Create Qualifications
        Qualification weighing = createQualification("WEIGHING", "Weighing", "Qualified for weighing operations");
        Qualification mixing = createQualification("MIXING", "Mixing", "Qualified for mixing operations");
        Qualification qaReview = createQualification("QA_REVIEW", "QA Review", "Qualified for quality assurance review");

        // Create Users
        User admin = createUser("admin", "admin123", "Administrator", UserRole.ADMIN);

        User supervisor = createUser("supervisor", "super123", "Production Supervisor", UserRole.SUPERVISOR);
        supervisor.addQualification(qaReview);

        User operator1 = createUser("operator1", "oper123", "Operator Kim", UserRole.OPERATOR);
        operator1.addQualification(weighing);
        operator1.addQualification(mixing);

        User operator2 = createUser("operator2", "oper123", "Operator Lee", UserRole.OPERATOR);
        operator2.addQualification(weighing);

        // Create Sample Recipe
        MasterRecipe recipe = createSampleRecipe(admin, weighing, mixing);

        log.info("Demo data initialized successfully!");
        log.info("===========================================");
        log.info("Test Accounts:");
        log.info("  admin / admin123 (ADMIN)");
        log.info("  supervisor / super123 (SUPERVISOR)");
        log.info("  operator1 / oper123 (OPERATOR - Weighing, Mixing)");
        log.info("  operator2 / oper123 (OPERATOR - Weighing only)");
        log.info("===========================================");
    }

    private Qualification createQualification(String code, String name, String description) {
        Qualification qualification = new Qualification(code, name, description);
        return qualificationRepository.save(qualification);
    }

    private User createUser(String username, String password, String name, UserRole role) {
        User user = new User(username, passwordEncoder.encode(password), name, role);
        return userRepository.save(user);
    }

    private MasterRecipe createSampleRecipe(User createdBy, Qualification weighing, Qualification mixing) {
        MasterRecipe recipe = new MasterRecipe("RCP-TABLET-001", "Tablet Production Recipe",
                "Standard recipe for producing pharmaceutical tablets");
        recipe.setCreatedBy(createdBy);

        // Step 1: Raw Material Weighing
        RecipeStep step1 = new RecipeStep(1, "Raw Material Weighing", "Weigh all raw materials according to specifications");
        step1.addRequiredQualification(weighing);
        step1.addParameter(new StepParameter("Material A Weight", ParameterType.NUMBER, true, 90.0, 110.0, "kg"));
        step1.addParameter(new StepParameter("Material B Weight", ParameterType.NUMBER, true, 45.0, 55.0, "kg"));
        step1.addParameter(new StepParameter("Balance ID", ParameterType.TEXT, true));
        step1.addParameter(new StepParameter("Weighing Verified", ParameterType.BOOLEAN, true));
        recipe.addStep(step1);

        // Step 2: Mixing
        RecipeStep step2 = new RecipeStep(2, "Dry Mixing", "Mix dry ingredients in the blender");
        step2.addRequiredQualification(mixing);
        step2.addParameter(new StepParameter("Mixing Time", ParameterType.NUMBER, true, 10.0, 30.0, "min"));
        step2.addParameter(new StepParameter("Mixer Speed", ParameterType.NUMBER, true, 100.0, 500.0, "rpm"));
        step2.addParameter(new StepParameter("Mixer Equipment ID", ParameterType.TEXT, true));
        recipe.addStep(step2);

        // Step 3: Granulation
        RecipeStep step3 = new RecipeStep(3, "Wet Granulation", "Add binder solution and granulate");
        step3.addRequiredQualification(mixing);
        step3.addParameter(new StepParameter("Binder Solution Volume", ParameterType.NUMBER, true, 5.0, 15.0, "L"));
        step3.addParameter(new StepParameter("Granulation Time", ParameterType.NUMBER, true, 5.0, 20.0, "min"));
        step3.addParameter(new StepParameter("End Point Reached", ParameterType.BOOLEAN, true));
        recipe.addStep(step3);

        // Step 4: Drying
        RecipeStep step4 = new RecipeStep(4, "Drying", "Dry the granules in fluid bed dryer");
        step4.addParameter(new StepParameter("Inlet Temperature", ParameterType.NUMBER, true, 50.0, 70.0, "C"));
        step4.addParameter(new StepParameter("Drying Time", ParameterType.NUMBER, true, 30.0, 90.0, "min"));
        step4.addParameter(new StepParameter("Final Moisture Content", ParameterType.NUMBER, true, 1.0, 3.0, "%"));
        step4.addParameter(new StepParameter("Dryer Equipment ID", ParameterType.TEXT, true));
        recipe.addStep(step4);

        // Step 5: Final Blending
        RecipeStep step5 = new RecipeStep(5, "Final Blending", "Add lubricant and perform final mixing");
        step5.addRequiredQualification(mixing);
        step5.addParameter(new StepParameter("Lubricant Weight", ParameterType.NUMBER, true, 0.5, 2.0, "kg"));
        step5.addParameter(new StepParameter("Final Blend Time", ParameterType.NUMBER, true, 3.0, 10.0, "min"));
        step5.addParameter(new StepParameter("Blend Uniformity Verified", ParameterType.BOOLEAN, true));
        recipe.addStep(step5);

        recipe.approve();  // Approve recipe so it can be used for batches

        return recipeRepository.save(recipe);
    }
}
