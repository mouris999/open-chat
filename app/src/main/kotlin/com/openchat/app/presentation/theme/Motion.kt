package com.openchat.app.presentation.theme

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp

/**
 * Central motion design-system for OpenChat.
 *
 * Every duration/easing in the app resolves through this file so transitions stay
 * consistent: the same gesture feels the same on every screen.
 *
 * Rules of thumb encoded here:
 *  - Small, frequent transitions (chips, icons, ripples) -> 150ms
 *  - Content entering/leaving a screen or expanding a panel -> 340ms
 *  - Anything the user can grab (FAB menus, sheets) -> spring, no fixed duration
 */
object Motion {

    /** Material 3 "emphasized" easing - fast start, long settle. Feels premium. */
    val Emphasized: Easing = CubicBezierEasing(0.2f, 0f, 0f, 1f)

    /** Standard decelerate curve for content that is arriving. */
    val Decelerate: Easing = CubicBezierEasing(0f, 0f, 0.2f, 1f)

    /** Standard accelerate curve for content that is leaving. */
    val Accelerate: Easing = CubicBezierEasing(0.4f, 0f, 1f, 1f)

    // ---- Durations -------------------------------------------------------

    /** Icons, ripples, small colour/opacity changes. */
    const val FAST = 150

    /** Chips, badges, button states. */
    const val MEDIUM = 250

    /** Screen-level content transitions, expanding panels. */
    const val SLOW = 340

    /** Splash logo entrance - slow enough to read as intentional. */
    const val DELIBERATE = 700

    // ---- Reusable specs --------------------------------------------------

    fun <T> fastSpec(): FiniteAnimationSpec<T> = tween(FAST, easing = Decelerate)
    fun <T> mediumSpec(): FiniteAnimationSpec<T> = tween(MEDIUM, easing = Decelerate)
    fun <T> slowSpec(): FiniteAnimationSpec<T> = tween(SLOW, easing = Decelerate)
    fun <T> exitSpec(): FiniteAnimationSpec<T> = tween(FAST + 60, easing = Accelerate)

    /** Spring used for anything the finger drives directly. */
    fun <T> bouncySpec(): FiniteAnimationSpec<T> =
        spring(dampingRatio = 0.72f, stiffness = Spring.StiffnessMediumLow)

    fun <T> gentleSpec(): FiniteAnimationSpec<T> =
        spring(dampingRatio = 0.9f, stiffness = Spring.StiffnessLow)

    /** Snappier spring for small elements that should feel alive, not bouncy. */
    fun <T> popSpec(): FiniteAnimationSpec<T> =
        spring(dampingRatio = 0.62f, stiffness = Spring.StiffnessMedium)

    // ---- Navigation transitions -----------------------------------------
    //
    // Forward navigation pushes the new screen in from the trailing edge while the
    // outgoing screen drifts slightly - a parallax "card stack" feel. Back navigation
    // mirrors it exactly so the motion is reversible and predictable.

    fun enterForward(): EnterTransition =
        slideInHorizontally(
            animationSpec = tween(SLOW, easing = Decelerate),
            initialOffsetX = { full -> full / 4 }
        ) + fadeIn(tween(FAST + 80, easing = Decelerate))

    fun exitForward(): ExitTransition =
        slideOutHorizontally(
            animationSpec = tween(SLOW, easing = Decelerate),
            targetOffsetX = { full -> -full / 8 }
        ) + fadeOut(tween(MEDIUM, easing = Accelerate))

    fun enterBack(): EnterTransition =
        slideInHorizontally(
            animationSpec = tween(SLOW, easing = Decelerate),
            initialOffsetX = { full -> -full / 8 }
        ) + fadeIn(tween(MEDIUM, easing = Decelerate))

    fun exitBack(): ExitTransition =
        slideOutHorizontally(
            animationSpec = tween(SLOW, easing = Decelerate),
            targetOffsetX = { full -> full / 4 }
        ) + fadeOut(tween(FAST + 80, easing = Accelerate))

    /** Modal / detail routes rise from the bottom - call screen, viewer, scanner. */
    fun enterModal(): EnterTransition =
        slideInVertically(
            animationSpec = tween(SLOW, easing = Decelerate),
            initialOffsetY = { full -> full / 3 }
        ) + fadeIn(tween(MEDIUM, easing = Decelerate))

    fun exitModal(): ExitTransition =
        slideOutVertically(
            animationSpec = tween(SLOW, easing = Accelerate),
            targetOffsetY = { full -> full / 3 }
        ) + fadeOut(tween(FAST, easing = Accelerate))

    fun enterModalBack(): EnterTransition =
        slideInVertically(
            animationSpec = tween(SLOW, easing = Decelerate),
            initialOffsetY = { full -> full / 6 }
        ) + fadeIn(tween(MEDIUM, easing = Decelerate))

    fun exitModalBack(): ExitTransition =
        slideOutVertically(
            animationSpec = tween(SLOW, easing = Accelerate),
            targetOffsetY = { full -> full / 3 }
        ) + fadeOut(tween(FAST, easing = Accelerate))

    // ---- Building blocks -------------------------------------------------

    /** Fade + rise, for content revealed inside an already-visible screen. */
    fun appear(): EnterTransition =
        fadeIn(tween(MEDIUM, easing = Decelerate)) +
            slideInVertically(tween(SLOW, easing = Decelerate)) { it / 8 }

    fun disappear(): ExitTransition =
        fadeOut(tween(FAST, easing = Accelerate)) +
            slideOutVertically(tween(MEDIUM, easing = Accelerate)) { it / 8 }

    /** Staggered entrance for list rows: index-aware delay so items cascade in. */
    fun staggeredAppear(index: Int, step: Int = 34, max: Int = 12): EnterTransition {
        val delay = index.coerceIn(0, max) * step
        return fadeIn(tween(MEDIUM + FAST, delayMillis = delay, easing = Decelerate)) +
            slideInVertically(tween(SLOW, delayMillis = delay, easing = Decelerate)) { it / 6 }
    }

    /** Scale-in used for FAB menus, popovers and reaction bubbles. */
    fun popIn(): EnterTransition =
        scaleIn(animationSpec = popSpec(), initialScale = 0.82f) +
            fadeIn(tween(FAST, easing = Decelerate))

    fun popOut(): ExitTransition =
        scaleOut(animationSpec = tween(FAST, easing = Accelerate), targetScale = 0.82f) +
            fadeOut(tween(FAST, easing = Accelerate))

    /** Content swap inside one slot (selected tab, loading<->content, theme change). */
    fun <S> contentSwap(): AnimatedContentTransitionScope<S>.() -> ContentTransform = {
        (slideInHorizontally(tween(MEDIUM, easing = Decelerate)) { it / 6 } +
            fadeIn(tween(FAST, easing = Decelerate))) togetherWith
            (slideOutHorizontally(tween(MEDIUM, easing = Accelerate)) { -it / 8 } +
                fadeOut(tween(FAST, easing = Accelerate)))
    }

    /** Pure crossfade - for two states occupying the same box. */
    fun <S> crossfadeSpec(): AnimatedContentTransitionScope<S>.() -> ContentTransform = {
        fadeIn(tween(MEDIUM, easing = Decelerate)) togetherWith
            fadeOut(tween(FAST, easing = Accelerate))
    }
}

/**
 * Reveals [content] with a fade + rise instead of an abrupt appear/disappear.
 * Used for the search field, inline pickers and empty states.
 */
@Composable
fun ExpandOnAppear(
    visible: Boolean,
    content: @Composable () -> Unit
) {
    AnimatedVisibility(
        visible = visible,
        enter = Motion.appear(),
        exit = Motion.disappear(),
        label = "ExpandOnAppear"
    ) {
        content()
    }
}

/**
 * Tinted press-feedback wrapper: scales and dims slightly while held.
 * Replaces bare `clickable` on large surfaces so taps feel responsive.
 */
fun Modifier.pressScale(
    pressed: Boolean,
    pressedScale: Float = 0.97f,
    pressedAlpha: Float = 0.9f
): Modifier = this
    .scale(if (pressed) pressedScale else 1f)
    .alpha(if (pressed) pressedAlpha else 1f)

/** Interpolates a colour pair by a 0..1 progress value - handy for press states. */
fun lerpColor(start: Color, stop: Color, fraction: Float): Color = lerp(start, stop, fraction)

/**
 * Standard press-scale modifier for list rows. Supply the interaction source from
 * the caller so the row can also opt out of the default ripple:
 *
 * ```
 * val interaction = remember { MutableInteractionSource() }
 * val pressed by interaction.collectIsPressedAsState()
 * Row(Modifier.clickable(interaction, null, onClick).rowPress(pressed)) { ... }
 * ```
 */
@Composable
fun Modifier.rowPress(pressed: Boolean, scale: Float = 0.985f): Modifier =
    if (pressed) this.scale(scale) else this

/** Fades + slides [this] visibility scope's content in, honouring [index]. */
fun AnimatedVisibilityScope.staggeredIn(index: Int): EnterTransition =
    Motion.staggeredAppear(index)

/**
 * Resolves a plain duration (ms) into a decelerate tween - the curve almost every
 * entering animation wants. Used at call sites where Motion's named specs do not
 * fit but the app-wide easing should still apply.
 */
fun <T> motionTween(durationMillis: Int, delayMillis: Int = 0): FiniteAnimationSpec<T> =
    tween(durationMillis, delayMillis = delayMillis, easing = Motion.Decelerate)
