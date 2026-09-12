Tani v3: brand integration applied to v2.
Original Return T SVG preserved in admin/brand.svg; Android paths translated from that asset.
Slogan, palette, light/dark resources, launcher/splash, navigation and admin updated.
XML/resource checks and ZIP integrity checked. APK build not run in this environment.
Static QA passed: XML/manifest, resource IDs/references, internal imports, admin DOM/JS syntax, SQL dependencies, baseline regression checks. Wrapper JAR integrity verified via Python (Java is unavailable locally).
QA resource scanner updated to recognize qualified resource directories, color selectors and generated style names.
