package com.falon.nosocialmedia.socialcounter.presentation.viewmodel

import com.falon.nosocialmedia.core.domain.flow.toCommonFlow
import com.falon.nosocialmedia.core.domain.flow.toCommonStateFlow
import com.falon.nosocialmedia.socialcounter.domain.interactor.IncreaseNoMediaCounterUseCase
import com.falon.nosocialmedia.socialcounter.domain.interactor.ObserveSocialMediaUseCase
import com.falon.nosocialmedia.socialcounter.domain.interactor.PopulateDatabaseUseCase
import com.falon.nosocialmedia.socialcounter.presentation.factory.NoSocialMediasStateFactory
import com.falon.nosocialmedia.socialcounter.presentation.mapper.NoSocialMediasViewStateMapper
import com.falon.nosocialmedia.socialcounter.presentation.model.HabitsEffect
import com.falon.nosocialmedia.socialcounter.presentation.model.KeyboardController
import com.github.michaelbull.result.filterErrors
import com.github.michaelbull.result.filterValues
import com.github.michaelbull.result.onFailure
import com.github.michaelbull.result.onSuccess
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.getAndUpdate
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class NoSocialMediasViewModel(
    viewStateFactory: NoSocialMediasStateFactory,
    coroutineScope: CoroutineScope?,
    private val viewStateMapper: NoSocialMediasViewStateMapper,
    private val increaseNoMediaCounterUseCase: IncreaseNoMediaCounterUseCase,
    populateDatabaseUseCase: PopulateDatabaseUseCase,
    observeSocialMediasUseCase: ObserveSocialMediaUseCase,
) {

    private val viewModelScope = coroutineScope ?: CoroutineScope(Dispatchers.Main + SupervisorJob())

    private val _state = MutableStateFlow(viewStateFactory.create())
    val viewState = _state
        .map { state -> viewStateMapper.from(state) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = viewStateMapper.from(_state.value)
        )
        .toCommonStateFlow()

    private val _effects = MutableStateFlow<List<HabitsEffect>>(emptyList())
    val effects = _effects.filter { it.isNotEmpty() }.toCommonFlow()

    init {
        populateDatabaseUseCase.execute().forEach {
            println("Error $it")
        }
        observeSocialMediasUseCase.execute()
            .onEach { socialMedias ->
                _state.value = _state.value.copy(noSocialsCounter = socialMedias.filterValues())

                socialMedias.filterErrors().onEach {
                    println("Got error $it")
                }
            }
            .catch { e ->
                println("Error observing social media use case: ${e.message}")
            }
            .launchIn(viewModelScope)
    }

    fun onSocialMediaClicked(id: Int) {
        increaseNoMediaCounterUseCase.execute(id)
            .onSuccess {
                println("SHOW TOAST TODO")
                // show toast
            }
            .onFailure {
                println("FAILURE $it")
            }

    }

    fun onFabClick() {
        _state.value = _state.value.copy(
            isBottomDialogVisible = true
        )
        viewModelScope.launch(Dispatchers.Default) {
            delay(150)
            _effects.sendEffect(HabitsEffect.RequestFocusOnNewHabit)
        }
    }

    fun onNewHabit() {
        _state.value = _state.value.copy(
            bottomDialogText = "",
            isBottomDialogVisible = false, // can be skipped
        )
        _effects.sendEffect(HabitsEffect.HideKeyboard) // can be skipped
    }

    fun onNewHabitTextChanged(text: String) {
        _state.value = _state.value.copy(
            bottomDialogText = text
        )
    }

    fun consumeEffect(): HabitsEffect =
        _effects.getAndUpdate { _effects.value.drop(1) }.first()

    fun onEffect(effect: HabitsEffect, keyboardWithFocusOnNewHabit: KeyboardController) {
        when(effect) {
            HabitsEffect.RequestFocusOnNewHabit -> keyboardWithFocusOnNewHabit.show()
            HabitsEffect.HideKeyboard -> keyboardWithFocusOnNewHabit.hide()
        }

    }

    private fun <T> MutableStateFlow<List<T>>.sendEffect(effect: T) {
        update { it + effect }
    }
}