package com.amirhparhizgar.utdiningwidget.data.model.uniconfig

/**
 * Created by AmirHossein Parhizgar on 4/20/2023.
 */
data class QomUniConfig(
    override val loginURL: String = "https://food.qom.ac.ir",
    override val reservesURL: String = "https://food.qom.ac.ir/Reserves",
    override val usernameField: String = "UserName",
    override val passwordField: String = "Password"
): UniConfig