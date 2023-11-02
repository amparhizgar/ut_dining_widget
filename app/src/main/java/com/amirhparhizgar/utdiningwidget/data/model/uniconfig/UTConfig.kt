package com.amirhparhizgar.utdiningwidget.data.model.uniconfig

/**
 * Created by AmirHossein Parhizgar on 4/20/2023.
 */
data class UTConfig(
    override val loginURL: String = "https://dining2.ut.ac.ir",
    override val reservesURL: String = "https://dining2.ut.ac.ir/Reserves",
    override val usernameField: String = "Username",
    override val passwordField: String = "password"
): UniConfig