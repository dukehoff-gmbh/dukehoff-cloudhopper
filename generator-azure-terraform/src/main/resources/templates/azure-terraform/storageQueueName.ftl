<#-- The storage queue Event Grid delivers the blob events of a container to: <name>-events, at most 63 characters -->
<#function storageEventQueue name>
  <#local base = name>
  <#if base?length gt 56><#local base = base[0..55]?remove_ending("-")></#if>
  <#return base + "-events">
</#function>
