package com.rst.heyrst.model

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/**
 * Offline on-device profile data model matching Formmate extension architecture.
 */
data class UserProfile(
    val id: String = UUID.randomUUID().toString(),
    val profileName: String = "",
    val fullName: String = "",
    val firstName: String = "",
    val lastName: String = "",
    val email: String = "",
    val phone: String = "",
    val dateOfBirth: String = "",
    val gender: String = "",
    val guardianName: String = "",
    val streetAddress: String = "",
    val city: String = "",
    val district: String = "",
    val state: String = "",
    val country: String = "India",
    val pincode: String = "",
    val age: String = "",
    val occupation: String = "",
    val course: String = "",
    val education: String = "",
    val institution: String = "",
    val isDefault: Boolean = false
) {
    fun toJson(): JSONObject {
        return JSONObject().apply {
            put("id", id)
            put("profileName", profileName)
            put("fullName", fullName)
            put("firstName", firstName)
            put("lastName", lastName)
            put("email", email)
            put("phone", phone)
            put("dateOfBirth", dateOfBirth)
            put("gender", gender)
            put("guardianName", guardianName)
            put("streetAddress", streetAddress)
            put("city", city)
            put("district", district)
            put("state", state)
            put("country", country)
            put("pincode", pincode)
            put("age", age)
            put("occupation", occupation)
            put("course", course)
            put("education", education)
            put("institution", institution)
            put("isDefault", isDefault)
        }
    }

    companion object {
        fun fromJson(json: JSONObject): UserProfile {
            return UserProfile(
                id = json.optString("id", UUID.randomUUID().toString()),
                profileName = json.optString("profileName", ""),
                fullName = json.optString("fullName", ""),
                firstName = json.optString("firstName", ""),
                lastName = json.optString("lastName", ""),
                email = json.optString("email", ""),
                phone = json.optString("phone", ""),
                dateOfBirth = json.optString("dateOfBirth", ""),
                gender = json.optString("gender", ""),
                guardianName = json.optString("guardianName", ""),
                streetAddress = json.optString("streetAddress", ""),
                city = json.optString("city", ""),
                district = json.optString("district", ""),
                state = json.optString("state", ""),
                country = json.optString("country", "India"),
                pincode = json.optString("pincode", ""),
                age = json.optString("age", ""),
                occupation = json.optString("occupation", ""),
                course = json.optString("course", ""),
                education = json.optString("education", ""),
                institution = json.optString("institution", ""),
                isDefault = json.optBoolean("isDefault", false)
            )
        }
    }
}

/**
 * Offline on-device storage manager for user profiles.
 * Strictly local, private to app, zero external transmission.
 */
object ProfileStorage {
    private const val PREFS_NAME = "heyrst_offline_profiles"
    private const val KEY_PROFILES = "saved_profiles_json"
    private const val KEY_ACTIVE_ID = "active_profile_id"

    fun getProfiles(context: Context): List<UserProfile> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val jsonStr = prefs.getString(KEY_PROFILES, null) ?: return emptyList()
        val list = mutableListOf<UserProfile>()
        try {
            val array = JSONArray(jsonStr)
            for (i in 0 until array.length()) {
                list.add(UserProfile.fromJson(array.getJSONObject(i)))
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return list
    }

    fun saveProfile(context: Context, profile: UserProfile) {
        val current = getProfiles(context).toMutableList()
        val index = current.indexOfFirst { it.id == profile.id }
        if (index >= 0) {
            current[index] = profile
        } else {
            // If first profile, make it active
            current.add(if (current.isEmpty()) profile.copy(isDefault = true) else profile)
        }
        persistProfiles(context, current)
        if (getActiveProfile(context) == null || current.size == 1) {
            setActiveProfileId(context, profile.id)
        }
    }

    fun deleteProfile(context: Context, profileId: String) {
        val current = getProfiles(context).filter { it.id != profileId }
        persistProfiles(context, current)
        if (getActiveProfileId(context) == profileId) {
            setActiveProfileId(context, current.firstOrNull()?.id ?: "")
        }
    }

    fun getActiveProfile(context: Context): UserProfile? {
        val profiles = getProfiles(context)
        if (profiles.isEmpty()) return null
        val activeId = getActiveProfileId(context)
        return profiles.find { it.id == activeId } ?: profiles.firstOrNull()
    }

    fun setActiveProfileId(context: Context, profileId: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_ACTIVE_ID, profileId).apply()
    }

    fun setActiveProfile(context: Context, profileId: String) {
        setActiveProfileId(context, profileId)
    }

    private fun getActiveProfileId(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_ACTIVE_ID, "") ?: ""
    }

    private fun persistProfiles(context: Context, profiles: List<UserProfile>) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val array = JSONArray()
        for (p in profiles) {
            array.put(p.toJson())
        }
        prefs.edit().putString(KEY_PROFILES, array.toString()).apply()
    }

    fun getValueForSemanticType(profile: UserProfile, semanticType: SemanticType): String? {
        val raw = when (semanticType) {
            SemanticType.FULL_NAME -> profile.fullName.ifBlank { "${profile.firstName} ${profile.lastName}".trim() }
            SemanticType.FIRST_NAME -> profile.firstName.ifBlank { profile.fullName.split(" ").firstOrNull() ?: "" }
            SemanticType.LAST_NAME -> profile.lastName.ifBlank { profile.fullName.split(" ").drop(1).joinToString(" ") }
            SemanticType.EMAIL -> profile.email
            SemanticType.PHONE -> profile.phone
            SemanticType.DATE_OF_BIRTH, SemanticType.DATE -> profile.dateOfBirth
            SemanticType.GENDER -> profile.gender
            SemanticType.GUARDIAN_NAME -> profile.guardianName
            SemanticType.ADDRESS -> profile.streetAddress.ifBlank {
                listOf(profile.city, profile.district, profile.state).filter { it.isNotBlank() }.joinToString(", ")
            }
            SemanticType.CITY -> profile.city
            SemanticType.DISTRICT -> profile.district
            SemanticType.STATE -> profile.state
            SemanticType.COUNTRY -> profile.country
            SemanticType.PINCODE -> profile.pincode
            SemanticType.AGE -> profile.age
            SemanticType.OCCUPATION -> profile.occupation
            SemanticType.COURSE -> profile.course
            SemanticType.EDUCATION -> profile.education
            SemanticType.INSTITUTION -> profile.institution
            else -> null
        }
        return raw?.takeIf { it.isNotBlank() }
    }

    /**
     * Identifies all scanned form fields that have matching saved information in the given user profile.
     */
    fun getMatchingFields(profile: UserProfile, fields: List<FormField>): List<Pair<FormField, String>> {
        val matches = mutableListOf<Pair<FormField, String>>()
        for (field in fields) {
            if (field.kind == FieldKind.CHECKBOX || field.semanticType == SemanticType.CONSENT) {
                continue
            }
            if (field.isFilled) {
                continue
            }
            val value = getValueForSemanticType(profile, field.semanticType)
            if (!value.isNullOrBlank()) {
                matches.add(Pair(field, value))
            }
        }
        return matches
    }
}
