package me.lightspeed7.dsug.consumer

import me.lightspeed7.dsug.MongoDB
import me.lightspeed7.dsug.Config
import reactivemongo.api.collections.default.BSONCollection

/**
 * This is a placeholder object for a real connection pool
 */
object ConnectionPool {

  def checkout = {

    val coll = MongoDB.getCollection(Config.MongodbCollection)
    coll
  }
  def release(coll: BSONCollection) = {} // no-op for MongoDB

}
