package co.chinho.readabilityreader.data.repository

enum class ReadStateAction(val wireValue: String) {
    READ("read"),
    UNREAD("unread"),
    SAVED("saved"),
    UNSAVED("unsaved"),
}
