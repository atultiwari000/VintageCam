package com.vintagecam.profiles.registry

import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import org.json.JSONArray
import org.json.JSONObject

@Singleton
class FilterRegistry @Inject constructor(
    @ApplicationContext private val context: Context,
    private val assetManager: AndroidFilterAssetManager,
) {
    private val loadedPresets: List<FilterPreset> by lazy { loadPresets() }

    fun getPresets(): List<FilterPreset> = loadedPresets

    fun getPresetById(id: String): FilterPreset? = loadedPresets.find { it.id == id }

    fun getDefaultPreset(): FilterPreset = loadedPresets.first()

    fun getProfileName(id: String): String = getPresetById(id)?.name ?: id

    fun getCameraProfiles() = loadedPresets.map { it.toCameraProfile() }

    private fun loadPresets(): List<FilterPreset> {
        val json = try {
            context.assets.open(REGISTRY_PATH).bufferedReader().use { it.readText() }
        } catch (e: Exception) {
            Log.e("FilterRegistry", "Unable to load $REGISTRY_PATH; using emergency defaults", e)
            return emergencyPresets()
        }

        val parsed = runCatching {
            val root = JSONObject(json)
            root.getJSONArray("filters").toFilterPresets()
        }.getOrElse { e ->
            Log.e("FilterRegistry", "Invalid $REGISTRY_PATH; using emergency defaults", e)
            emergencyPresets()
        }

        parsed.forEach { preset ->
            listOfNotNull(
                preset.assets.lut,
                preset.assets.overlay,
                preset.assets.grain,
                preset.assets.frame,
            ).forEach { path ->
                if (assetManager.assetStatus(path) == AssetStatus.MISSING) {
                    Log.w("FilterRegistry", "Preset ${preset.id} references missing asset $path; parameter fallback will be used")
                }
            }
        }

        return parsed.ifEmpty { emergencyPresets() }
    }

    private fun JSONArray.toFilterPresets(): List<FilterPreset> = List(length()) { index ->
        getJSONObject(index).toFilterPreset()
    }

    private fun JSONObject.toFilterPreset(): FilterPreset {
        val parametersJson = getJSONObject("parameters")
        val assetsJson = optJSONObject("assets") ?: JSONObject()
        val hintsJson = optJSONObject("captureHints") ?: JSONObject()
        return FilterPreset(
            id = getString("id"),
            name = getString("name"),
            category = enumValue(optString("category"), FilterCategory.ERA_1990S),
            tier = enumValue(optString("tier"), FilterTier.FREE),
            parameters = parametersJson.toFilterParameters(),
            assets = assetsJson.toFilterAssets(),
            captureHints = hintsJson.toCaptureHints(),
            effects = optJSONArray("effects").toEffectList(),
            unlockCondition = optJSONObject("unlockCondition")?.let {
                UnlockCondition(type = it.optString("type"), value = it.optString("value"))
            },
        )
    }

    private fun JSONObject.toFilterParameters(): FilterParameters = FilterParameters(
        grain = optDouble("grain", 0.0).toFloat(),
        vignette = optDouble("vignette", 0.0).toFloat(),
        vignetteMid = optDouble("vignetteMid", 0.45).toFloat(),
        vignetteOuter = optDouble("vignetteOuter", 0.85).toFloat(),
        fade = optDouble("fade", 0.0).toFloat(),
        saturation = optDouble("saturation", 1.0).toFloat(),
        contrast = optDouble("contrast", 1.0).toFloat(),
        halation = optDouble("halation", 0.0).toFloat(),
        chromaticAberration = optDouble("chromaticAberration", 0.0).toFloat(),
        barrel = optDouble("barrel", 0.0).toFloat(),
        tintShadow = optFloatArray("tintShadow"),
        tintHighlight = optFloatArray("tintHighlight"),
        lutStrength = optDouble("lutStrength", 0.0).toFloat(),
        grainSize = enumValue(optString("grainSize"), FilterParameters.GrainSize.MEDIUM),
        crushedBlacks = optDouble("crushedBlacks", 0.0).toFloat(),
        shadowNoiseIntensity = optDouble("shadowNoiseIntensity", 0.0).toFloat(),
    )

    private fun JSONObject.toFilterAssets(): FilterAssets {
        return FilterAssets(
            lut = optNullableString("lut"),
            overlay = optNullableString("overlay"),
            grain = optNullableString("grain"),
            frame = optNullableString("frame"),
            assetStatus = enumValue(optString("assetStatus"), AssetStatus.PLACEHOLDER),
        )
    }

    private fun JSONObject.toCaptureHints(): CaptureHints = CaptureHints(
        latencyMs = optLong("latencyMs", 80L),
        aspectRatio = optString("aspectRatio", "RATIO_4_3"),
        viewfinderType = optString("viewfinderType", "OPTICAL"),
        dateStampStyle = optString("dateStampStyle", "NONE"),
        flashBehavior = optString("flashBehavior", "OFF"),
        deviceLabel = optString("deviceLabel", ""),
        soundId = optString("soundId", "default"),
    )

    private fun JSONArray?.toEffectList(): List<FilterEffect> {
        if (this == null) return listOf(FilterEffect.MASTER_GRADE)
        return List(length()) { index ->
            enumValue(getString(index), FilterEffect.MASTER_GRADE)
        }.distinct()
    }

    private fun JSONObject.optFloatArray(key: String): FloatArray {
        val array = optJSONArray(key) ?: return floatArrayOf(0f, 0f, 0f)
        return FloatArray(3) { index -> array.optDouble(index, 0.0).toFloat() }
    }

    private fun JSONObject.optNullableString(key: String): String? {
        if (!has(key) || isNull(key)) return null
        return optString(key).takeIf { it.isNotBlank() && it != "null" }
    }

    private inline fun <reified T : Enum<T>> enumValue(raw: String?, fallback: T): T {
        if (raw.isNullOrBlank()) return fallback
        return runCatching { enumValueOf<T>(raw) }.getOrDefault(fallback)
    }

    private fun emergencyPresets(): List<FilterPreset> = listOf(
        FilterPreset(
            id = "disposable_1998",
            name = "FunSaver '98",
            category = FilterCategory.ERA_1990S,
            tier = FilterTier.FREE,
            parameters = FilterParameters(
                grain = 0.62f,
                vignette = 0.72f,
                saturation = 0.82f,
                contrast = 0.88f,
                chromaticAberration = 0.009f,
                barrel = 0.09f,
                grainSize = FilterParameters.GrainSize.COARSE,
            ),
            assets = FilterAssets(),
            captureHints = CaptureHints(
                latencyMs = 80L,
                aspectRatio = "RATIO_3_2",
                viewfinderType = "OPTICAL",
                dateStampStyle = "YELLOW_CLASSIC",
                flashBehavior = "AUTO",
                deviceLabel = "Disposable",
            ),
            effects = listOf(FilterEffect.MASTER_GRADE, FilterEffect.BLUE_NOISE_GRAIN, FilterEffect.DATE_STAMP, FilterEffect.FLASH_FALLOFF),
        ),
    )

    private companion object {
        const val REGISTRY_PATH = "filters/filter_registry.json"
    }
}
