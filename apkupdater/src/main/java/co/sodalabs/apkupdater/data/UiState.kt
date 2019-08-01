package co.sodalabs.apkupdater.data

sealed class UiState<T> {

    class InProgress<T> : UiState<T>()

    data class Done<T>(
        val data: T
    ) : UiState<T>()
}