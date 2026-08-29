# Responsive and accessibility

Target WCAG 2.2 AA for every P0 customer and staff flow.

## Responsive behavior

| Width | Booking behavior |
|---|---|
| 390 mobile | Single column; compact sticky total/action; calendar cells remain 44px targets |
| 768 tablet | Single content column with full summary below options |
| 1024 desktop | Options plus sticky summary rail |
| 1440 wide | Maximum content width 1280px; no uncontrolled stretching |

No supported width may horizontally scroll at document level. Zoom at 200%
must preserve task completion.

## Interaction requirements

- Full keyboard operation with visible focus.
- Touch targets at least 44×44px.
- Real buttons/links/inputs, not clickable generic containers.
- Labels stay associated with controls; placeholders are examples only.
- Validation identifies the field and recovery action.
- Focus moves to the step heading after a checkout transition.
- Dynamic total and availability messages use polite live regions.
- Dialogs trap focus, close with Escape where safe and restore trigger focus.

## Visual and language requirements

- Text contrast at least 4.5:1; large text and non-text UI at least 3:1.
- Status never relies on color alone.
- Reduced-motion preference disables decorative transitions.
- `html lang` and `dir` update with locale; content containers mirror them.
- Arabic layouts are authored and tested, not CSS-flipped screenshots.
- Images require meaningful alt text or empty alt when decorative.
- Service media needs rights and focal-point metadata before publication.

## Test matrix

Chrome desktop/mobile viewport, keyboard-only happy path, English LTR, Arabic
RTL, long content, 200% zoom, reduced motion, loading/empty/error/unavailable,
failed payment and expired session.
