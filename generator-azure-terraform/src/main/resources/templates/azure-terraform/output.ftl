output "${handlerInfo.functionId}_url" {
  value = "https://${"$"}{azurerm_linux_function_app.shared_function_app.default_hostname}/api${path}"
}
