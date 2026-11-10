package app.rshmap.core

/**
 * Built-in starting points. Positions are approximations of each game's default HUD in landscape;
 * use the in-app overlay editor to nudge them onto your own layout.
 */
object Templates {
    private const val AR = 16f / 9f

    private fun circle(id: String, type: ElementType, label: String, x: Float, y: Float, key: Int, size: Float = 0.05f) =
        Element(id = id, type = type, label = label, x = x, y = y, w = size, h = size * AR, keys = listOf(key))

    private fun stick(x: Float, y: Float, size: Float = 0.13f, modifier: Int = Keys.SHIFT_LEFT, modScale: Float = 0.45f) =
        Element(
            id = "move", type = ElementType.JOYSTICK, label = "Move", x = x, y = y, w = size, h = size * AR,
            keys = listOf(Keys.W, Keys.A, Keys.S, Keys.D), modifierKey = modifier, modifierScale = modScale,
        )

    private fun look(l: Float, t: Float, r: Float, b: Float) =
        Element(id = "look", type = ElementType.MOUSE_LOOK, label = "Look", x = (l + r) / 2f, y = (t + b) / 2f, w = r - l, h = b - t)

    private fun fps(
        name: String,
        pkg: String,
        fire: Pair<Float, Float> = 0.88f to 0.58f,
        scope: Pair<Float, Float> = 0.80f to 0.40f,
        jump: Pair<Float, Float> = 0.92f to 0.76f,
        crouch: Pair<Float, Float> = 0.82f to 0.86f,
        prone: Pair<Float, Float> = 0.93f to 0.90f,
        joy: Pair<Float, Float> = 0.17f to 0.70f,
        lookLeft: Float = 0.45f,
        ads: Float = 0.55f,
        autoBurst: Boolean = false,
    ): Profile = Profile(
        name = name,
        packageName = pkg,
        mouse = MouseSettings(sensX = 1.0f, sensY = 1.0f, adsMultiplier = ads),
        elements = listOf(
            stick(joy.first, joy.second),
            look(lookLeft, 0.05f, 1.0f, 0.75f),
            circle("fire", ElementType.FIRE, "Fire", fire.first, fire.second, Keys.MOUSE_LEFT, 0.08f).copy(autoBurst = autoBurst),
            circle("scope", ElementType.SCOPE, "Scope", scope.first, scope.second, Keys.MOUSE_RIGHT, 0.055f),
            circle("jump", ElementType.TAP, "Jump", jump.first, jump.second, Keys.SPACE),
            circle("crouch", ElementType.TAP, "Crouch", crouch.first, crouch.second, Keys.C),
            circle("prone", ElementType.TAP, "Prone", prone.first, prone.second, Keys.Z),
            circle("reload", ElementType.TAP, "Reload", 0.72f, 0.82f, Keys.R),
            circle("interact", ElementType.TAP, "Pick up", 0.62f, 0.55f, Keys.F),
            circle("slot1", ElementType.TAP, "Slot 1", 0.58f, 0.90f, Keys.N1),
            circle("slot2", ElementType.TAP, "Slot 2", 0.66f, 0.90f, Keys.N2),
            circle("grenade", ElementType.TAP, "Grenade", 0.50f, 0.90f, Keys.G),
            circle("heal", ElementType.TAP, "Heal", 0.40f, 0.90f, Keys.H),
            circle("leanL", ElementType.HOLD, "Lean L", 0.30f, 0.33f, Keys.Q, 0.04f),
            circle("leanR", ElementType.HOLD, "Lean R", 0.40f, 0.33f, Keys.E, 0.04f),
            circle("wheelUp", ElementType.SCROLL, "Prev wpn", 0.58f, 0.90f, Keys.WHEEL_UP, 0.04f),
            circle("wheelDown", ElementType.SCROLL, "Next wpn", 0.66f, 0.90f, Keys.WHEEL_DOWN, 0.04f),
        ),
    )

    private fun moba(): Profile = Profile(
        name = "Generic MOBA",
        mouse = MouseSettings(),
        elements = listOf(
            stick(0.16f, 0.68f, 0.14f, Keys.NONE).copy(modifierScale = 1f),
            circle("attack", ElementType.TAP, "Attack", 0.88f, 0.82f, Keys.MOUSE_LEFT, 0.08f),
            circle("skill1", ElementType.HOLD, "Skill 1", 0.72f, 0.84f, Keys.Q, 0.06f),
            circle("skill2", ElementType.HOLD, "Skill 2", 0.76f, 0.66f, Keys.E, 0.06f),
            circle("skill3", ElementType.HOLD, "Skill 3", 0.88f, 0.56f, Keys.R, 0.06f),
            circle("spell1", ElementType.TAP, "Spell 1", 0.68f, 0.60f, Keys.F, 0.045f),
            circle("spell2", ElementType.TAP, "Spell 2", 0.62f, 0.72f, Keys.D, 0.045f),
            circle("recall", ElementType.TAP, "Recall", 0.55f, 0.88f, Keys.B, 0.045f),
            circle("shop", ElementType.TAP, "Shop", 0.10f, 0.12f, Keys.TAB, 0.045f),
        ),
    )

    private fun racing(): Profile = Profile(
        name = "Generic Racing",
        elements = listOf(
            circle("left", ElementType.HOLD, "Left", 0.10f, 0.80f, Keys.A, 0.09f),
            circle("right", ElementType.HOLD, "Right", 0.24f, 0.80f, Keys.D, 0.09f),
            circle("gas", ElementType.HOLD, "Gas", 0.90f, 0.74f, Keys.W, 0.11f),
            circle("brake", ElementType.HOLD, "Brake", 0.78f, 0.84f, Keys.S, 0.09f),
            circle("nitro", ElementType.TAP, "Nitro", 0.88f, 0.50f, Keys.SPACE, 0.07f),
            circle("drift", ElementType.HOLD, "Drift", 0.70f, 0.70f, Keys.SHIFT_LEFT, 0.07f),
            circle("look", ElementType.TAP, "Camera", 0.50f, 0.10f, Keys.C, 0.05f),
        ),
    )

    private fun minecraft(): Profile = Profile(
        name = "Minecraft PE",
        packageName = "com.mojang.minecraftpe",
        mouse = MouseSettings(sensX = 0.9f, sensY = 0.9f),
        elements = listOf(
            stick(0.15f, 0.72f, 0.14f),
            look(0.40f, 0.05f, 1.0f, 0.80f),
            circle("break", ElementType.FIRE, "Break/Attack", 0.70f, 0.50f, Keys.MOUSE_LEFT, 0.06f),
            circle("place", ElementType.TAP, "Place/Use", 0.74f, 0.50f, Keys.MOUSE_RIGHT, 0.06f),
            circle("jump", ElementType.HOLD, "Jump", 0.90f, 0.68f, Keys.SPACE, 0.07f),
            circle("sneak", ElementType.TOGGLE, "Sneak", 0.90f, 0.84f, Keys.SHIFT_LEFT, 0.06f),
            circle("inv", ElementType.TAP, "Inventory", 0.54f, 0.93f, Keys.E, 0.05f),
            circle("drop", ElementType.TAP, "Drop", 0.60f, 0.76f, Keys.Q, 0.04f),
            circle("hotPrev", ElementType.SCROLL, "Hotbar <", 0.36f, 0.93f, Keys.WHEEL_UP, 0.04f),
            circle("hotNext", ElementType.SCROLL, "Hotbar >", 0.64f, 0.93f, Keys.WHEEL_DOWN, 0.04f),
        ),
    )

    private fun genshin(): Profile = Profile(
        name = "Genshin Impact",
        packageName = "com.miHoYo.GenshinImpact",
        mouse = MouseSettings(sensX = 0.9f, sensY = 0.9f),
        elements = listOf(
            stick(0.15f, 0.70f, 0.14f),
            look(0.45f, 0.05f, 1.0f, 0.60f),
            circle("attack", ElementType.FIRE, "Attack", 0.90f, 0.78f, Keys.MOUSE_LEFT, 0.10f),
            circle("skill", ElementType.TAP, "Skill", 0.80f, 0.86f, Keys.E, 0.07f),
            circle("burst", ElementType.TAP, "Burst", 0.72f, 0.72f, Keys.Q, 0.07f),
            circle("sprint", ElementType.HOLD, "Sprint", 0.90f, 0.58f, Keys.MOUSE_RIGHT, 0.07f),
            circle("jump", ElementType.TAP, "Jump", 0.94f, 0.44f, Keys.SPACE, 0.07f),
            circle("interact", ElementType.TAP, "Interact", 0.62f, 0.50f, Keys.F, 0.05f),
            circle("char1", ElementType.TAP, "Char 1", 0.96f, 0.14f, Keys.N1, 0.045f),
            circle("char2", ElementType.TAP, "Char 2", 0.96f, 0.26f, Keys.N2, 0.045f),
            circle("char3", ElementType.TAP, "Char 3", 0.96f, 0.38f, Keys.N3, 0.045f),
            circle("char4", ElementType.TAP, "Char 4", 0.96f, 0.50f, Keys.N4, 0.045f),
            circle("map", ElementType.TAP, "Map", 0.10f, 0.12f, Keys.M, 0.04f),
        ),
    )

    private fun fpsList(): List<Profile> = listOf(
        fps("BGMI", "com.pubg.imobile", fire = 0.90f to 0.60f, scope = 0.82f to 0.40f, lookLeft = 0.50f, ads = 0.55f),
        fps("PUBG Mobile", "com.tencent.ig", fire = 0.90f to 0.60f, scope = 0.82f to 0.40f, lookLeft = 0.50f, ads = 0.55f),
        fps("Free Fire", "com.dts.freefireth", fire = 0.88f to 0.56f, scope = 0.78f to 0.38f, jump = 0.92f to 0.80f, crouch = 0.80f to 0.88f, ads = 0.60f),
        fps("Free Fire MAX", "com.dts.freefiremax", fire = 0.88f to 0.56f, scope = 0.78f to 0.38f, jump = 0.92f to 0.80f, crouch = 0.80f to 0.88f, ads = 0.60f),
        fps("COD Mobile", "com.activision.callofduty.shooter", fire = 0.88f to 0.60f, scope = 0.78f to 0.42f, autoBurst = false, ads = 0.55f),
        fps("Generic FPS", ""),
    )

    /** All built-in templates. Templates with an empty packageName are generic. */
    fun all(): List<Profile> = fpsList() + listOf(minecraft(), genshin(), moba(), racing())

    fun forPackage(pkg: String): Profile? = all().firstOrNull { it.packageName == pkg }
}
