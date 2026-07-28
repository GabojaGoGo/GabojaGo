from gabojago_data_import.mapping import resolve_place_type


def test_restaurant_cafe_category_is_mapped_to_cafe() -> None:
    assert resolve_place_type("39", "A05020900") == "CAFE"


def test_restaurant_category_is_mapped_to_restaurant() -> None:
    assert resolve_place_type("39", "A05020100") == "RESTAURANT"


def test_unknown_content_type_is_skipped() -> None:
    assert resolve_place_type("999", None) is None
