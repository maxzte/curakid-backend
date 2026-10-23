package com.misterio.curakid.feature.auth

class ConsentRequiredException(
    message: String,
) : Exception(message)

class LegalDocumentNotFoundException(
    message: String,
) : Exception(message)

class LegalDocumentAlreadyActivatedException(
    message: String,
) : Exception(message)

class LegalDocumentAlreadyExistException(
    message: String,
) : Exception(message)
