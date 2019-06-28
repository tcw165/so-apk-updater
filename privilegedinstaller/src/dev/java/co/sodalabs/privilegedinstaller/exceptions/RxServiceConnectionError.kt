package co.sodalabs.privilegedinstaller.exceptions

class RxServiceConnectionError(componentName: String) : Throwable("Unable to bind $componentName")