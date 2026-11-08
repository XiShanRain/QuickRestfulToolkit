package com.xishanrain.restful.common.resolver

import com.xishanrain.restful.navigation.action.RestServiceItem

interface ServiceResolver {
    fun findAllSupportedServiceItemsInModule(): List<RestServiceItem>
    fun findAllSupportedServiceItemsInProject(): List<RestServiceItem>
}
