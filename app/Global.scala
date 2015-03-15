// package default

import play.api.Application
import play.api.GlobalSettings
import me.lightspeed7.dsug.Actors

object Global extends GlobalSettings {

  override def onStart(app: Application) {

    // initialize actors
    Actors.generator

  }

  override def onStop(app: Application) {
    //
  }

}
