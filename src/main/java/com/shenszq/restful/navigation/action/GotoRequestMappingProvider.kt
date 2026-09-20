package com.shenszq.restful.navigation.action

import com.intellij.ide.util.gotoByName.ChooseByNameItemProvider
import com.intellij.ide.util.gotoByName.ChooseByNameModel
import com.intellij.ide.util.gotoByName.ChooseByNameViewModel
import com.intellij.ide.util.gotoByName.DefaultChooseByNameItemProvider
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.psi.PsiElement
import com.intellij.util.Processor
import com.shenszq.restful.common.UrlPatternUtils

class GotoRequestMappingProvider(context: PsiElement?) : DefaultChooseByNameItemProvider(context) {
    override fun filterElements(
        base: ChooseByNameViewModel,
        pattern: String,
        everywhere: Boolean,
        indicator: ProgressIndicator,
        consumer: Processor<Any>
    ): Boolean {
        val normalizedPattern = UrlPatternUtils.normalizeUserInput(pattern)
        val model: ChooseByNameModel = base.model
        val processed = linkedSetOf<Any>()
        for (name in model.getNames(everywhere)) {
            indicator.checkCanceled()
            if (!UrlPatternUtils.matches(name, normalizedPattern)) continue
            for (element in model.getElementsByName(name, everywhere, normalizedPattern)) {
                indicator.checkCanceled()
                if (!processed.add(element)) continue
                if (!consumer.process(element)) return false
            }
        }
        return true
    }
}
