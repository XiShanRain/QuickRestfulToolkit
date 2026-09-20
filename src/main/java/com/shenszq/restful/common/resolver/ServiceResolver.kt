package com.shenszq.restful.common.resolver

import com.shenszq.restful.navigation.action.RestServiceItem

interface ServiceResolver {
    fun findAllSupportedServiceItemsInModule(): List<RestServiceItem>
    fun findAllSupportedServiceItemsInProject(): List<RestServiceItem>
}
