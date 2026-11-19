package org.tidalex.rempart.core.progress

/** Messages affichés à la place d'une page bloquée. Ton bienveillant, jamais culpabilisant. */
object Encouragements {
    val MESSAGES = listOf(
        "Ce moment va passer. L'envie monte, culmine, puis redescend — souvent en moins de 15 minutes.",
        "Tu as installé Rempart pour une bonne raison. Cette raison est toujours vraie maintenant.",
        "Respire : inspire 4 secondes, bloque 4 secondes, expire 6 secondes. Recommence trois fois.",
        "Lève-toi, bois un verre d'eau, change de pièce. Le corps qui bouge calme l'esprit.",
        "Chaque refus renforce le cerveau que tu veux avoir. Tu viens d'en gagner un.",
        "Fatigué, seul, stressé, ennuyé ? Ce sont des signaux, pas des ordres. De quoi as-tu vraiment besoin ?",
        "Appelle ou écris à quelqu'un, même pour parler d'autre chose. L'isolement nourrit l'envie.",
        "Une rechute n'efface pas ton chemin. Ce qui compte, c'est ce que tu fais maintenant.",
    )

    fun forDay(dayIndex: Long): String = MESSAGES[(dayIndex.mod(MESSAGES.size.toLong())).toInt()]
}
