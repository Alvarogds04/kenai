package com.kenai.android.core

/**
 * The Widgets retired in KN-13 (S3), each with where its function lives now, so the app can say it in Settings instead of a user losing a
 * Widget without a word (L6). Two tables, EN and ES, picked like [Words] with [of]; the names are the ones the picker used, so the
 * explanation matches what the home screen still shows for one release.
 */
class RetiredWidgets private constructor(val entries: List<Retired>) {
    /** One retired Widget: the name the picker used, and the one plain line saying where it went. */
    data class Retired(val name: String, val where: String)

    companion object {
        private val EN = RetiredWidgets(
            listOf(
                Retired("All your agents", "Its rows are now «Sessions & activity», the 4x3 size."),
                Retired("Approvals waiting", "Its pending rows and its button are now in «Sessions & activity»."),
                Retired("Finished today", "The count is now a line in «Sessions & activity», marked approx. (this phone)."),
                Retired("Agents' mood", "Kenai's face is now in «Sessions & activity» and in «At a glance»."),
            ),
        )
        private val ES = RetiredWidgets(
            listOf(
                Retired("Todos tus agentes", "Sus filas están ahora en «Sesiones y actividad», el tamaño 4x3."),
                Retired("Aprobaciones pendientes", "Sus filas pendientes y su botón están ahora en «Sesiones y actividad»."),
                Retired("Terminadas hoy", "El conteo es ahora una línea en «Sesiones y actividad», marcada aprox. (este teléfono)."),
                Retired("Ánimo de los agentes", "La cara de Kenai está ahora en «Sesiones y actividad» y en «De un vistazo»."),
            ),
        )

        /** The table for [w]: Spanish for [Words.ES], English otherwise, like [Words.current]. */
        fun of(w: Words): RetiredWidgets = if (w === Words.ES) ES else EN
    }
}
