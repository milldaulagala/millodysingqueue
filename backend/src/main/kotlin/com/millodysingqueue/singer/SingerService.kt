package com.millodysingqueue.singer

class SingerService(private val repository: SingerRepository) {
    suspend fun register(request: CreateSingerRequest): Singer {
        val name = request.singerName?.trim().orEmpty()
        val nickname = request.nickName?.trim()?.ifEmpty { null }
        val email = request.email?.trim()?.ifEmpty { null }
        val errors = linkedMapOf<String, String>()

        when {
            name.isBlank() -> errors["singerName"] = "Enter the singer's name."
            name.length > 100 -> errors["singerName"] = "Use 100 characters or fewer."
            name.any { it.isISOControl() } -> errors["singerName"] = "Use a name without control characters."
        }
        when {
            nickname != null && nickname.length > 100 -> errors["nickName"] = "Use 100 characters or fewer."
            nickname != null && nickname.any { it.isISOControl() } -> errors["nickName"] = "Use a nickname without control characters."
        }
        if (email != null && (email.length > 254 || !EMAIL.matches(email))) {
            errors["email"] = "Enter a valid email address, such as mill@example.com."
        }
        if (errors.isNotEmpty()) throw SingerValidationException(errors)
        return repository.create(NewSinger(name, nickname, email))
    }

    suspend fun list(): List<Singer> = repository.list()

    companion object {
        // Practical address validation, not mailbox verification. Preserve casing.
        private val EMAIL = Regex("^[A-Za-z0-9!#$%&'*+/=?^_`{|}~-]+(?:\\.[A-Za-z0-9!#$%&'*+/=?^_`{|}~-]+)*@[A-Za-z0-9](?:[A-Za-z0-9-]*[A-Za-z0-9])?(?:\\.[A-Za-z0-9](?:[A-Za-z0-9-]*[A-Za-z0-9])?)+$")
    }
}
