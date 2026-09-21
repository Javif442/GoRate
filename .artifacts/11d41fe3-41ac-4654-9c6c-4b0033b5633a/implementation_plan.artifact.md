# Fix Radar Animation in RadarView

The user reported that the radar "doesn't spin like before". This implementation plan focuses on fixing the animation in `RadarView.kt` to ensure it is robust, visible, and performs well.

## User Review Required

> [!NOTE]
> I will improve the visual appearance of the radar sweep (the "tail") to make it more visible, as the current implementation had a very narrow sweep (only 25% of the circle).

## Proposed Changes

### [RadarView Component]

#### [MODIFY] [RadarView.kt](file:///C:/Users/Cristhian Franco/Desktop/GoRate/app/src/main/java/com/gorate/app/presentation/custom/RadarView.kt)
- Improve the `SweepGradient` to cover the full circle (0.0 to 1.0) for a better "fading tail" effect.
- Simplify the `onDraw` logic by using a `Matrix` to rotate the shader instead of rotating the entire canvas. This is more efficient and avoids potential issues with canvas transformations.
- Ensure the animation is correctly managed during view lifecycle events (`onAttachedToWindow`, `onDetachedFromWindow`).
- Fix the `onDraw` check to ensure the radar draws whenever the animator is active.

## Verification Plan

### Automated Tests
- N/A (UI Animation logic is best verified manually).

### Manual Verification
1. Launch the app.
2. Enable the GoRate service.
3. Observe the radar in the main dashboard. It should spin smoothly.
4. Toggle "Turbo Mode" and verify the spin speed increases.
5. Minimize the app and return to verify the animation resumes correctly.
