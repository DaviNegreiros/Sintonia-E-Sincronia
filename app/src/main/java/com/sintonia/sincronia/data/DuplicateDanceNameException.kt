package com.sintonia.sincronia.data

class DuplicateDanceNameException(title: String) : IllegalArgumentException(
    "O nome \"$title\" já está em uso."
)
