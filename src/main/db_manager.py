import os
import pymysql
from datetime import datetime

class DatabaseManager:
    """
    Shared database utility class for RDS MySQL interactions within an AWS VPC.
    Encapsulates operations for Orders, Order Items, and Notifications.
    """
    def __init__(self):
        self.host = os.environ.get('DB_HOST')
        self.user = os.environ.get('DB_USER')
        self.password = os.environ.get('DB_PASSWORD')
        self.database = os.environ.get('DB_NAME')
        self.port = int(os.environ.get('DB_PORT', 3306))

    def _get_connection(self):
        return pymysql.connect(
            host=self.host,
            user=self.user,
            password=self.password,
            database=self.database,
            port=self.port,
            cursorclass=pymysql.cursors.DictCursor,
            connect_timeout=5
        )

    def create_order(self, user_id: int, items: list) -> int:
        """
        Inserts a new order and its associated order items atomically.
        items format: [{'item_id': 1, 'quantity': 2}, ...]
        """
        connection = self._get_connection()
        try:
            with connection.cursor() as cursor:
                order_query = "INSERT INTO `Orders` (`created_at`, `user_id`) VALUES (%s, %s)"
                cursor.execute(order_query, (datetime.utcnow(), user_id))
                order_id = cursor.lastrowid

                item_query = "INSERT INTO `order_items` (`order_id`, `item_id`, `quantity`) VALUES (%s, %s, %s)"
                for item in items:
                    cursor.execute(item_query, (order_id, item['item_id'], item['quantity']))

            connection.commit()
            return order_id
        except Exception as e:
            connection.rollback()
            raise e
        finally:
            connection.close()

    def add_notification(self, user_id: int, message: str, notification_type: str) -> int:
        """
        Inserts a notification record linked to a customer.
        """
        connection = self._get_connection()
        try:
            with connection.cursor() as cursor:
                query = "INSERT INTO `Notification` (`user_id`, `message`, `type`) VALUES (%s, %s, %s)"
                cursor.execute(query, (user_id, message, notification_type))
            connection.commit()
            return cursor.lastrowid
        except Exception as e:
            connection.rollback()
            raise e
        finally:
            connection.close()