package com.misterio.curakid.feature.auth

class ConsentRequiredException(
    message: String,
) : Exception()

class LegalDocumentNotFoundException(
    message: String,
) : Exception()

class LegalDocumentAlreadyActivatedException(
    message: String,
) : Exception()

class LegalDocumentAlreadyExistException(
    message: String,
) : Exception()
