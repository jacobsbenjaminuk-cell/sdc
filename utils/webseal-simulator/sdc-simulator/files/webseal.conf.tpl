{
	webseal {
		fe="${FE_URL}"
		portalCookieName="EPService"
		#Space separated list of permitted ancestors
		permittedAncestors="${PERMITTED_ANCESTORS}"
		users = [
			{
				userId="cs0008"
				password="${SIMULATOR_PASSWORD}"
				firstName="Carlos"
				lastName="Santana"
				role="Designer"
				email="csantana@sdc.com"
			},
			{
				userId="jh0003"
				password="${SIMULATOR_PASSWORD}"
				firstName="Jimmy"
				lastName="Hendrix"
				role="Admin"
				email="admin@sdc.com"
			},
			{
				userId="jm0007"
				password="${SIMULATOR_PASSWORD}"
				firstName="Johnny"
				lastName="Depp"
				role="Tester"
				email="tester@sdc.com"
			}
		]
	}

}
