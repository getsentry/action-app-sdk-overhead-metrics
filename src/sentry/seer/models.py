from pydantic import BaseModel


class SeerRepoDefinition(BaseModel):
    provider: str
    owner: str
    name: str
    external_id: str
