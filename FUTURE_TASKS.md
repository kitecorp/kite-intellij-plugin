# Future Tasks

## ~~String Interpolation Navigation (Cmd+Click)~~ COMPLETED

**Status:** Implemented

**Test file:** `KiteStringInterpolationNavigationTest.java`

**Issue:** When using Cmd+Click on `$region` in a string interpolation like:

```kite
import region from "common.kite"

var endpoint = "https://$region.api.example.com"
```

**Decision:** **Option B was implemented** - Navigate directly to the declaration in `common.kite`

- Goes directly to the source of truth
- Consistent with how regular identifier navigation works
- Both `$var` and `${var}` syntax are supported

**Implementation:**

- Fixed `KiteGotoDeclarationHandler.java` to search imported files for INTERP_IDENTIFIER and INTERP_SIMPLE tokens
- Added fallback to `findDeclarationInImportedFiles()` when local declaration not found
- Works for named imports, wildcard imports, and local variables

**Related files:**

- `KiteGotoDeclarationHandler.java` - Navigation handler
- `KiteStringInterpolationNavigationTest.java` - Test coverage
