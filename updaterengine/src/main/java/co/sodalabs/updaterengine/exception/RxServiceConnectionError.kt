package co.sodalabs.updaterengine.exception

class RxServiceConnectionError(componentName: String) : Throwable("Unable to bind $componentName")