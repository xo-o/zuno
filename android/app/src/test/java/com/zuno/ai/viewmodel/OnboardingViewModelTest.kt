package com.zuno.ai.viewmodel

import com.zuno.ai.data.AppLanguage
import com.zuno.ai.data.LearningLanguages
import com.zuno.ai.data.OnboardingTurnResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OnboardingViewModelTest {

    @Test
    fun startsAtUserLanguageAndProgressesThroughChatOnlyFlow() {
        val vm = OnboardingViewModel()
        assertEquals(OnboardingStep.UserLanguage, vm.state.value.step)
        assertEquals(AppLanguage.English, vm.state.value.selectedAppLanguage)

        vm.selectAppLanguage(AppLanguage.Spanish)
        assertEquals(AppLanguage.Spanish, vm.state.value.selectedAppLanguage)

        vm.continueFromUserLanguage()
        assertEquals(OnboardingStep.Language, vm.state.value.step)

        val french = LearningLanguages.find("fr")
        vm.selectLanguage(french)
        assertEquals("fr", vm.state.value.selectedLanguageCode)
        assertEquals(french.defaultAccent, vm.state.value.selectedAccentId)

        vm.continueFromLanguage("Hola, soy Zuno. ¿Cómo te gustaría que te llame?")
        assertEquals(OnboardingStep.Name, vm.state.value.step)
        assertEquals(1, vm.state.value.chatHistory.size)
        assertEquals(OnboardingChatRole.Assistant, vm.state.value.chatHistory.first().role)

        vm.applyParsedTurn(
            step = OnboardingStep.Name,
            userDisplayText = "Noah",
            parsed = OnboardingTurnResult(step = "name", valid = true, name = "Noah"),
            levelPromptBuilder = { fn -> "Mucho gusto, $fn." },
            goalsPromptBuilder = { fn -> "¿Por qué aprendes, $fn?" },
        )
        assertEquals(OnboardingStep.Level, vm.state.value.step)
        assertEquals("Noah", vm.state.value.firstName)
        assertEquals(3, vm.state.value.chatHistory.size)

        vm.selectLevelChip("Intermediate")
        assertEquals(OnboardingStep.Level, vm.state.value.step)
        assertEquals("Intermediate", vm.state.value.draftText)

        vm.selectLevelChip("Beginner")
        assertEquals("Beginner", vm.state.value.draftText)

        vm.selectLevelChip("Beginner")
        assertEquals("", vm.state.value.draftText)

        vm.selectLevelChip("Intermediate")
        vm.updateDraft("Intermediate — I can handle everyday basics.")
        assertEquals("Intermediate — I can handle everyday basics.", vm.state.value.draftText)

        vm.applyParsedTurn(
            step = OnboardingStep.Level,
            userDisplayText = vm.state.value.draftText,
            parsed = OnboardingTurnResult(step = "level", valid = true, level = "B1"),
            levelPromptBuilder = { fn -> "Mucho gusto, $fn." },
            goalsPromptBuilder = { fn -> "¿Por qué aprendes, $fn?" },
        )
        assertEquals(OnboardingStep.Goals, vm.state.value.step)
        assertEquals("B1", vm.state.value.level)
        assertEquals("", vm.state.value.draftText)

        vm.toggleGoalChip("Travel")
        assertTrue("Travel" in vm.state.value.selectedGoalChips)
        assertEquals("Travel", vm.state.value.draftText)

        vm.toggleGoalChip("Work")
        assertEquals(setOf("Travel", "Work"), vm.state.value.selectedGoalChips)
        assertEquals("Travel, Work", vm.state.value.draftText)

        vm.toggleGoalChip("Travel")
        assertFalse("Travel" in vm.state.value.selectedGoalChips)
        assertEquals("Work", vm.state.value.draftText)

        vm.updateDraft("Work — travel in France")
        assertTrue("Work" in vm.state.value.selectedGoalChips)
        vm.updateDraft("Travel in France")
        assertFalse("Work" in vm.state.value.selectedGoalChips)

        vm.applyParsedTurn(
            step = OnboardingStep.Goals,
            userDisplayText = "Travel in France",
            parsed = OnboardingTurnResult(
                step = "goals",
                valid = true,
                focus = "travel",
                interests = listOf("Travel", "Food"),
                goalNote = "Travel in France",
            ),
            levelPromptBuilder = { "" },
            goalsPromptBuilder = { "" },
        )
        assertEquals(OnboardingStep.Preparing, vm.state.value.step)
        assertEquals("travel", vm.state.value.focus)
        assertEquals(listOf("Travel", "Food"), vm.state.value.interests)
    }

    @Test
    fun stepBackWalksBackThroughUserLanguageAndExitsAtFirstStep() {
        val vm = OnboardingViewModel()
        assertFalse(vm.stepBack("Name?") { "Level?" })

        vm.continueFromUserLanguage()
        assertEquals(OnboardingStep.Language, vm.state.value.step)

        vm.continueFromLanguage("Name?")
        assertEquals(OnboardingStep.Name, vm.state.value.step)

        assertTrue(vm.stepBack("Name?") { "Level?" })
        assertEquals(OnboardingStep.Language, vm.state.value.step)

        assertTrue(vm.stepBack("Name?") { "Level?" })
        assertEquals(OnboardingStep.UserLanguage, vm.state.value.step)

        assertFalse(vm.stepBack("Name?") { "Level?" })
    }
}
