.. This work is licensed under a Creative Commons Attribution 4.0 International License.
.. http://creativecommons.org/licenses/by/4.0
.. Copyright 2023 Nordix

.. _useradministration:

===================
User Administration
===================

.. contents::
   :depth: 3
..

Initial User Creation
---------------------

During initial install following users are created:

::

    {
      {
        "userId": "demo",
        "firstName": "demo",
        "lastName": "demo",
        "role": "ADMIN",
        "email": "demo@openecomp.org"
      },
      {
        "userId": "jh0003",
        "firstName": "Jimmy",
        "lastName": "Hendrix",
        "role": "Admin",
        "email": "jh0003@openecomp.org"
      },
      {
        "userId": "jm0007",
        "firstName": "Joni",
        "lastName": "Mitchell",
        "role": "TESTER",
        "email": "jm0007@openecomp.org"
      },
      {
        "userId": "cs0008",
        "firstName": "Carlos",
        "lastName": "Santana",
        "role": "DESIGNER",
        "email": "cs0008r@openecomp.org"
      }
    }

Default User
------------

SDC does not assign a default user. A request with no identity is rejected. For an isolated development setup only, the frontend can serve
anonymous visitors as a fixed user by setting ``allowAnonymousDefaultUser: true`` and ``defaultUserId`` in the catalog-fe configuration.yaml;
the frontend logs a warning at startup when this is on. To choose the user explicitly see section :ref:`Using Cookies to set User <using_cookies>`.


Using Cookies to set User
-------------------------
.. _using_cookies:

The user can be set with the following cookie in your browser or API call:

::

    USER_ID:<any existed user (created by initial install or by Administrator)>
