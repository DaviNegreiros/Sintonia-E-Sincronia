package com.sintonia.sincronia.processing

class NoPoseDetectedException : IllegalStateException(
    "Nenhuma pessoa foi detectada no vídeo. Verifique iluminação, enquadramento e se o corpo inteiro aparece."
)

