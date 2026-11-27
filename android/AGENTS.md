# Android App Agent Guidelines

## Responsive Horizontal Spacing (`zunoLayout`)

- **Never hardcode `ZunoSpace.s24` (or `24.dp`) for horizontal screen gutters.** Always use `zunoLayout` (`LocalZunoLayout.current`) from `com.zuno.ai.ui.theme.ZunoTheme`:
  - `zunoLayout.screenPaddingH` for screen `Column` / `LazyColumn` / `Row` horizontal margins (`16.dp` on `< 420dp` phones such as Xiaomi 14T, `20.dp` on `420–599dp`, `24.dp` on `>= 600dp`).
  - `zunoLayout.topBarPaddingH` for top bars using 44dp `ZunoIconButton` (`6.dp` on `< 420dp`, so `6dp + 10dp` icon inset aligns the 24dp glyph at `16.dp` with screen content).
  - `zunoLayout.topBarPillEndPadding` for top bars with trailing pills (`ZunoSelectorTopBar`, `ZunoSidebarHomeTopBar`).
  - `zunoLayout.cardPaddingH`, `zunoLayout.cardTrailingMenuEndPadding`, and `zunoLayout.rowGap` for cards, list rows, and search fields.
  - `zunoLayout.chipPaddingH`, `zunoLayout.chipGap`, and `zunoLayout.tabTrackPadding` for filter chip strips and `ZunoSegmentedTabs`.
  - `zunoLayout.modalPaddingH` for dialogs (`ZunoDialogCard`).
- Keep `ZunoSpace.s24` for vertical spacing, vertical padding, or large button pill padding (`ZunoButtonSize.Large`).
- Keep `ZunoLayoutTest.kt` passing when changing breakpoints or token values.

## Build & Verification

Run from `android/`:

```bash
./gradlew testDebugUnitTest compileDebugAndroidTestKotlin
```
